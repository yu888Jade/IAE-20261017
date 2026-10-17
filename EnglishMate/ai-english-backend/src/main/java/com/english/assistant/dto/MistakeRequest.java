package com.english.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MistakeRequest {
    @NotNull(message = "作文记录ID不能为空")
    private Long essayRecordId;

    @NotBlank(message = "原始错误内容不能为空")
    @Size(max = 3000, message = "原始错误内容过长")
    private String originalText;

    @NotBlank(message = "修改后的内容不能为空")
    @Size(max = 3000, message = "修改后的内容过长")
    private String correctedText;

    @NotBlank(message = "错误说明不能为空")
    @Size(max = 5000, message = "错误说明过长")
    private String explanation;

    @NotBlank(message = "错题来源不能为空")
    @Pattern(regexp = "^essay$", message = "不支持的错题来源")
    private String source;

    public MistakeRequest() {}

    public Long getEssayRecordId() { return essayRecordId; }
    public void setEssayRecordId(Long essayRecordId) { this.essayRecordId = essayRecordId; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
