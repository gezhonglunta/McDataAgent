package com.alibaba.cloud.ai.dataagent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author 张华
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McPageSearchVO {
	private String mcBaseUrl;
	private String pageId;
	private String areaCode;
	private List<String> objectName;
	private String funcCode;
	private String rowPermissionSql;
}
