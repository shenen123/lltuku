package com.liubinrui.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@TableName(value ="picture")
@Data
public class Picture {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    private String thumbUrl; // 新增：缩略图预签名 URL

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    @TableField("tags")
    @JsonIgnore
    private String tagsJson;
    // 内存中使用的 List 字段
    @TableField(exist = false)
    private List<String> tags; // 若标签实体是 QuestionTag，改为 List<QuestionTag>
    // 自动将 tagsJson ↔ tags 双向转换（原 getOptions → getTags）
    public List<String> getTags() { // 返回值类型同步调整
        if (this.tags != null) {
            return this.tags;
        }
        if (this.tagsJson == null || this.tagsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.tags = objectMapper.readValue(
                    this.tagsJson,
                    new TypeReference<List<String>>() {
                    }
            );
            return this.tags;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tags JSON", e); // 异常信息同步修改
        }
    }

    // 原 setOptions → setTags
    public void setTags(List<String> tags) { // 参数名和类型同步调整
        this.tags = tags;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.tagsJson = objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tags to JSON", e); // 异常信息同步修改
        }
    }

    /**
     * 图片体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 编辑时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date editTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 删除时间
     */
    @TableField(fill = FieldFill.UPDATE)
    private Date deleteTime;

    /**
     * 状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 id
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private Date reviewTime;
    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
