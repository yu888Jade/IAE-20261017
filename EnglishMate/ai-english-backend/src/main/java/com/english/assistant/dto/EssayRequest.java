package com.english.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EssayRequest {
    @NotBlank(message = "作文内容不能为空")
    @Size(min = 20, max = 10000, message = "作文内容长度应在20到10000个字符之间")
    private String essay;

    @NotBlank(message = "考试等级不能为空")
    @Pattern(regexp = "(?i)^(cet4|cet6)$", message = "考试等级仅支持 cet4 或 cet6")
    private String level;

    @NotBlank(message = "请求标识不能为空")
    @Size(max = 80, message = "请求标识过长")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "请求标识格式不合法")
    private String requestId;

    public EssayRequest() {}

    public String getEssay() { return essay; }
    public void setEssay(String essay) { this.essay = essay; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
