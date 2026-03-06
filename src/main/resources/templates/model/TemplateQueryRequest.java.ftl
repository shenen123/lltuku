package ${packageName}.model.dto.${dataKey};

import ${packageName}.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ${upperDataKey}QueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long notId;

    private String searchText;

    private String title;

    private String content;

    private List<String> tags;

    private Long userId;

    private static final long serialVersionUID = 1L;
}