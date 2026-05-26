package com.l1ght.ebe.projection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectionEntityTransforms {
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private ProjectionEntityTransforms() {
    }

    public static CompoundTag prepareForProjection(CompoundTag source, ProjectionData projection) {
        if (source == null || projection == null || !isAllowedDecorativeEntityTag(source)) return null;
        Vec3 localPos = readPos(source);

        CompoundTag copy = source.copy();
        copy.remove("UUID");
        copy.remove("UUIDMost");
        copy.remove("UUIDLeast");

        if (localPos != null) {
            Vec3 transformed = transformPos(localPos, projection);
            writePos(copy, transformed);
        }
        transformHangingAttachment(copy, projection);
        transformRotation(copy, projection.getRotation(), projection.getMirror());
        return copy;
    }

    public static Vec3 readPos(CompoundTag tag) {
        if (tag == null || !tag.contains("Pos", 9)) return null;
        ListTag pos = tag.getList("Pos", 6);
        if (pos.size() < 3) return null;
        return new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }

    public static BlockPos blockPos(CompoundTag tag) {
        if (tag != null && tag.contains("TileX") && tag.contains("TileY") && tag.contains("TileZ")) {
            return new BlockPos(tag.getInt("TileX"), tag.getInt("TileY"), tag.getInt("TileZ"));
        }
        Vec3 pos = readPos(tag);
        return pos == null ? BlockPos.ZERO : BlockPos.containing(pos);
    }

    public static boolean isAllowedDecorativeEntityTag(CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return false;
        return isAllowedDecorativeEntityType(tag.getString("id"));
    }

    public static boolean isAllowedDecorativeEntityType(String rawId) {
        if (rawId == null || rawId.isBlank()) return false;
        Optional<EntityType<?>> optional = EntityType.byString(rawId);
        if (optional.isEmpty()) return false;
        EntityType<?> type = optional.get();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return false;
        String path = id.getPath();
        if (isDeniedEntityPath(path)) return false;
        if (type == EntityType.ARMOR_STAND
                || type == EntityType.ITEM_FRAME
                || type == EntityType.GLOW_ITEM_FRAME
                || type == EntityType.PAINTING
                || type == EntityType.BLOCK_DISPLAY
                || type == EntityType.ITEM_DISPLAY
                || type == EntityType.TEXT_DISPLAY
                || type == EntityType.LEASH_KNOT) {
            return true;
        }
        return type.getCategory() == MobCategory.MISC;
    }

    public static boolean isAllowedDecorativeEntity(Entity entity) {
        if (entity == null || !entity.shouldBeSaved()) return false;
        if (entity instanceof ArmorStand || entity instanceof HangingEntity || entity instanceof Display) return true;
        if (entity instanceof Mob
                || entity instanceof ItemEntity
                || entity instanceof ExperienceOrb
                || entity instanceof Projectile
                || entity instanceof VehicleEntity
                || entity instanceof PrimedTnt
                || entity instanceof FallingBlockEntity
                || entity instanceof AreaEffectCloud
                || entity instanceof Marker) {
            return false;
        }
        ResourceLocation id = EntityType.getKey(entity.getType());
        if (id == null || isDeniedEntityPath(id.getPath())) return false;
        return entity.getType().getCategory() == MobCategory.MISC;
    }

    public static void stabilizeRenderableEntity(Entity entity) {
        if (entity == null) return;
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setOnGround(true);
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();
        entity.xRotO = entity.getXRot();
        entity.yRotO = entity.getYRot();
        setFloatField(entity, "xo", (float) entity.getX());
        setFloatField(entity, "yo", (float) entity.getY());
        setFloatField(entity, "zo", (float) entity.getZ());
        setFloatField(entity, "yBodyRotO", entity.getYRot());
        setFloatField(entity, "yHeadRotO", entity.getYRot());
        setFloatField(entity, "yBodyRot", entity.getYRot());
        setFloatField(entity, "yHeadRot", entity.getYRot());
        setFloatField(entity, "animationPosition", 0.0F);
        setFloatField(entity, "animationSpeed", 0.0F);
        setFloatField(entity, "animationSpeedOld", 0.0F);
        setFloatField(entity, "bob", 0.0F);
        setFloatField(entity, "oBob", 0.0F);
        freezeWalkAnimation(entity);
        entity.tickCount = 1;
    }

    private static void freezeWalkAnimation(Entity entity) {
        Object walkAnimation = getFieldValue(entity, "walkAnimation");
        if (walkAnimation == null) return;
        invokeFloatMethod(walkAnimation, "setSpeed", 0.0F);
        invokeFloatMethod(walkAnimation, "setPosition", 0.0F);
        setFloatField(walkAnimation, "speed", 0.0F);
        setFloatField(walkAnimation, "speedOld", 0.0F);
        setFloatField(walkAnimation, "position", 0.0F);
    }

    private static Vec3 transformPos(Vec3 localPos, ProjectionData projection) {
        Rotation rotation = projection.getRotation();
        Mirror mirror = projection.getMirror();
        if (rotation == Rotation.NONE && mirror == Mirror.NONE) {
            BlockPos origin = projection.getOrigin();
            return new Vec3(localPos.x + origin.getX(), localPos.y + origin.getY(), localPos.z + origin.getZ());
        }

        BlockPos localCenterBlock = projection.getCenterPoint().subtract(projection.getOrigin());
        Vec3 localCenter = new Vec3(localCenterBlock.getX(), localCenterBlock.getY(), localCenterBlock.getZ());
        Vec3 relative = localPos.subtract(localCenter);
        Vec3 rotated = rotate(relative, rotation);
        Vec3 mirrored = mirror(rotated, mirror);
        BlockPos center = projection.getCenterPoint();
        return new Vec3(center.getX() + mirrored.x, center.getY() + mirrored.y, center.getZ() + mirrored.z);
    }

    private static BlockPos transformLocalBlockPos(BlockPos localPos, ProjectionData projection) {
        Rotation rotation = projection.getRotation();
        Mirror mirror = projection.getMirror();
        if (rotation == Rotation.NONE && mirror == Mirror.NONE) {
            BlockPos origin = projection.getOrigin();
            return origin.offset(localPos);
        }

        BlockPos localCenterBlock = projection.getCenterPoint().subtract(projection.getOrigin());
        BlockPos relativeBlock = localPos.subtract(localCenterBlock);
        Vec3 relative = new Vec3(relativeBlock.getX(), relativeBlock.getY(), relativeBlock.getZ());
        Vec3 rotated = rotate(relative, rotation);
        Vec3 mirrored = mirror(rotated, mirror);
        BlockPos center = projection.getCenterPoint();
        return new BlockPos(center.getX() + (int) Math.round(mirrored.x),
                center.getY() + (int) Math.round(mirrored.y),
                center.getZ() + (int) Math.round(mirrored.z));
    }

    private static void transformHangingAttachment(CompoundTag tag, ProjectionData projection) {
        if (tag == null || projection == null) return;
        if (tag.contains("TileX") && tag.contains("TileY") && tag.contains("TileZ")) {
            BlockPos transformed = transformLocalBlockPos(
                    new BlockPos(tag.getInt("TileX"), tag.getInt("TileY"), tag.getInt("TileZ")),
                    projection);
            tag.putInt("TileX", transformed.getX());
            tag.putInt("TileY", transformed.getY());
            tag.putInt("TileZ", transformed.getZ());
        }
        if (tag.contains("Facing")) {
            Direction direction = Direction.from3DDataValue(tag.getByte("Facing"));
            direction = transformDirection(direction, projection.getRotation(), projection.getMirror());
            tag.putByte("Facing", (byte) direction.get3DDataValue());
        }
        if (tag.contains("Direction")) {
            Direction direction = Direction.from3DDataValue(tag.getByte("Direction"));
            direction = transformDirection(direction, projection.getRotation(), projection.getMirror());
            tag.putByte("Direction", (byte) direction.get3DDataValue());
        }
        if (tag.contains("facing")) {
            Direction direction = Direction.byName(tag.getString("facing"));
            if (direction != null) {
                direction = transformDirection(direction, projection.getRotation(), projection.getMirror());
                tag.putString("facing", direction.getSerializedName());
            }
        }
    }

    private static Direction transformDirection(Direction direction, Rotation rotation, Mirror mirror) {
        if (direction == null) return Direction.NORTH;
        Direction transformed = direction;
        if (mirror == Mirror.LEFT_RIGHT && transformed.getAxis() == Direction.Axis.Z) {
            transformed = transformed.getOpposite();
        } else if (mirror == Mirror.FRONT_BACK && transformed.getAxis() == Direction.Axis.X) {
            transformed = transformed.getOpposite();
        }
        return rotation == Rotation.NONE ? transformed : rotation.rotate(transformed);
    }

    private static Vec3 rotate(Vec3 pos, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new Vec3(-pos.z, pos.y, pos.x);
            case CLOCKWISE_180 -> new Vec3(-pos.x, pos.y, -pos.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(pos.z, pos.y, -pos.x);
            default -> pos;
        };
    }

    private static Vec3 mirror(Vec3 pos, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> new Vec3(pos.x, pos.y, -pos.z);
            case FRONT_BACK -> new Vec3(-pos.x, pos.y, pos.z);
            default -> pos;
        };
    }

    private static void writePos(CompoundTag tag, Vec3 pos) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(pos.x));
        list.add(DoubleTag.valueOf(pos.y));
        list.add(DoubleTag.valueOf(pos.z));
        tag.put("Pos", list);
    }

    private static void transformRotation(CompoundTag tag, Rotation rotation, Mirror mirror) {
        if (!tag.contains("Rotation", 9)) return;
        ListTag list = tag.getList("Rotation", 5);
        if (list.size() < 2) return;
        float yaw = list.getFloat(0);
        float pitch = list.getFloat(1);
        yaw = switch (rotation) {
            case CLOCKWISE_90 -> yaw + 90.0F;
            case CLOCKWISE_180 -> yaw + 180.0F;
            case COUNTERCLOCKWISE_90 -> yaw - 90.0F;
            default -> yaw;
        };
        if (mirror == Mirror.LEFT_RIGHT) {
            yaw = 180.0F - yaw;
        } else if (mirror == Mirror.FRONT_BACK) {
            yaw = -yaw;
        }
        ListTag rotated = new ListTag();
        rotated.add(FloatTag.valueOf(wrapDegrees(yaw)));
        rotated.add(FloatTag.valueOf(pitch));
        tag.put("Rotation", rotated);
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) degrees -= 360.0F;
        if (degrees < -180.0F) degrees += 360.0F;
        return degrees;
    }

    private static boolean isDeniedEntityPath(String path) {
        if (path == null || path.isBlank()) return true;
        return path.contains("projectile")
                || path.contains("bullet")
                || path.contains("arrow")
                || path.contains("thrown")
                || path.contains("falling")
                || path.contains("tnt")
                || path.contains("minecart")
                || path.contains("boat")
                || path.contains("lightning")
                || path.contains("marker")
                || path.contains("experience")
                || path.contains("item_entity")
                || path.contains("area_effect");
    }

    private static void setFloatField(Entity entity, String name, float value) {
        setFloatField((Object) entity, name, value);
    }

    private static void setFloatField(Object target, String name, float value) {
        if (target == null) return;
        Field field = findField(target.getClass(), name);
        if (field == null) return;
        try {
            if (field.getType() == float.class) {
                field.setFloat(target, value);
            } else if (field.getType() == double.class) {
                field.setDouble(target, value);
            }
        } catch (Exception ignored) {
        }
    }

    private static Object getFieldValue(Object target, String name) {
        if (target == null) return null;
        Field field = findField(target.getClass(), name);
        if (field == null) return null;
        try {
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void invokeFloatMethod(Object target, String name, float value) {
        if (target == null) return;
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                var method = current.getDeclaredMethod(name, float.class);
                method.setAccessible(true);
                method.invoke(target, value);
                return;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Exception ignored) {
                return;
            }
        }
    }

    private static Field findField(Class<?> type, String name) {
        String key = type.getName() + "#" + name;
        return FIELD_CACHE.computeIfAbsent(key, ignored -> {
            Class<?> current = type;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored2) {
                    current = current.getSuperclass();
                }
            }
            return null;
        });
    }
}
