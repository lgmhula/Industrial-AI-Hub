package dev.reboot.dto.ai;

/**
 * RAG 文档入库结果（Week 11 Day 74）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class RagIngestResult {

    private String fileName;
    private Integer characters;
    private Integer chunks;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getCharacters() { return characters; }
    public void setCharacters(Integer characters) { this.characters = characters; }
    public Integer getChunks() { return chunks; }
    public void setChunks(Integer chunks) { this.chunks = chunks; }

    @Override
    public String toString() {
        return "RagIngestResult{fileName=" + fileName
                + ", characters=" + characters
                + ", chunks=" + chunks + "}";
    }
}
