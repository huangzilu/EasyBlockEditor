package com.l1ght.ebe.data;

public class BuildingMetadata {
    private String name = "Untitled";
    private String author = "";
    private String description = "";
    private int sizeX, sizeY, sizeZ;
    private long created;
    private long modified;

    public BuildingMetadata() {
        this.created = System.currentTimeMillis();
        this.modified = this.created;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.modified = System.currentTimeMillis(); }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public void setSize(int x, int y, int z) { this.sizeX = x; this.sizeY = y; this.sizeZ = z; }
    public long getCreated() { return created; }
    public void setCreated(long created) { this.created = created; }
    public long getModified() { return modified; }
    public void setModified(long modified) { this.modified = modified; }
}
