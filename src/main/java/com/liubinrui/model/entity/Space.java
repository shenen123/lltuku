package com.liubinrui.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value ="space")
@Data
public class Space implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    private Long maxSize;

    private Long maxCount;

    /**
     * 当前空间下图片的总大小
     */
    private Long totalSize;

    /**
     * 当前空间下的图片数量
     */
    private Long totalCount;

    private Long userId;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}