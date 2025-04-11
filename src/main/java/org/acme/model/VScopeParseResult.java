package org.acme.model;

public class VScopeParseResult {
    private Integer videoId;

    private String stream;

    private String vtt;

    public Integer getVideoId() {
        return videoId;
    }

    public void setVideoId(Integer videoId) {
        this.videoId = videoId;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getVtt() {
        return vtt;
    }

    public void setVtt(String vtt) {
        this.vtt = vtt;
    }

    public VScopeParseResult(Integer videoId, String stream, String vtt) {
        this.videoId = videoId;
        this.stream = stream;
        this.vtt = vtt;
    }
}
