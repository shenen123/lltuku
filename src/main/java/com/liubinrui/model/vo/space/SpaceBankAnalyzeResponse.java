package com.liubinrui.model.vo.space;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceBankAnalyzeResponse implements Serializable {
    private Long spaceId;
    private Double sizeRate;
    private static final long serialVersionUID = 1L;

}
