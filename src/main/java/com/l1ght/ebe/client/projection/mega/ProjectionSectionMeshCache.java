package com.l1ght.ebe.client.projection.mega;

import com.mojang.blaze3d.vertex.VertexBuffer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectionSectionMeshCache implements AutoCloseable {
    private final int maxMeshes;
    private final LinkedHashMap<Long, MeshHandle> meshes;

    public ProjectionSectionMeshCache(int maxMeshes) {
        this.maxMeshes = Math.max(16, maxMeshes);
        this.meshes = new LinkedHashMap<>(64, 0.75F, true);
    }

    public MeshHandle get(long sectionKey) {
        return meshes.get(sectionKey);
    }

    public MeshHandle getOrCreate(long sectionKey) {
        MeshHandle handle = meshes.get(sectionKey);
        if (handle != null) {
            return handle;
        }
        handle = new MeshHandle(sectionKey);
        meshes.put(sectionKey, handle);
        trim();
        return handle;
    }

    public void remove(long sectionKey) {
        MeshHandle handle = meshes.remove(sectionKey);
        if (handle != null) {
            handle.close();
        }
    }

    public void clear() {
        for (var mesh : meshes.values()) {
            mesh.close();
        }
        meshes.clear();
    }

    public int size() {
        return meshes.size();
    }

    private void trim() {
        Iterator<Map.Entry<Long, MeshHandle>> iterator = meshes.entrySet().iterator();
        while (meshes.size() > maxMeshes && iterator.hasNext()) {
            var eldest = iterator.next();
            eldest.getValue().close();
            iterator.remove();
        }
    }

    @Override
    public void close() {
        clear();
    }

    public static final class MeshHandle implements AutoCloseable {
        private final long sectionKey;
        private final VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        private boolean compiled;
        private boolean hasData;
        private boolean failed;

        private MeshHandle(long sectionKey) {
            this.sectionKey = sectionKey;
        }

        public long sectionKey() {
            return sectionKey;
        }

        public VertexBuffer vertexBuffer() {
            return vertexBuffer;
        }

        public boolean compiled() {
            return compiled;
        }

        public void setCompiled(boolean compiled) {
            this.compiled = compiled;
        }

        public boolean hasData() {
            return hasData;
        }

        public void setHasData(boolean hasData) {
            this.hasData = hasData;
        }

        public boolean failed() {
            return failed;
        }

        public void setFailed(boolean failed) {
            this.failed = failed;
        }

        @Override
        public void close() {
            vertexBuffer.close();
        }
    }
}
