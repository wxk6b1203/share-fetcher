package com.github.ak.fetcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票基本面信息表 (历史版本)
 * 数据来源: stock_individual_basic_info_xq (雪球)
 * 说明: 当 mainOperationBusiness/operatingScope/legalRepresentative 变化时新增版本
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_fundamental")
public class StockFundamental {
    @TableId(type = IdType.AUTO)
    private Long id;                     // 主键ID
    private String code;                 // 股票代码 (6位纯数字)
    private String orgId;               // 组织ID (如: 03130417)
    private String orgNameCn;           // 公司中文全称 (如: 三一重工股份有限公司)
    private String orgShortNameCn;     // 公司中文简称 (如: 三一重工)
    private String orgNameEn;          // 公司英文全称
    private String orgShortNameEn;     // 公司英文简称
    private String mainOperationBusiness;  // 主营业务 (如: 工程机械的研发、制造、销售和服务)
    private String operatingScope;     // 经营范围
    private String districtEncode;     // 行政区划代码 (如: 110114)
    private String orgCnIntroduction; // 公司简介
    private String legalRepresentative;  // 法定代表人
    private String generalManager;    // 总经理
    private String secretary;         // 董事会秘书
    private String establishedDate;  // 成立日期 (时间戳格式)
    private BigDecimal regAsset;     // 注册资本 (元)
    private Integer staffNum;        // 员工人数
    private String telephone;        // 电话
    private String postcode;        // 邮编
    private String fax;             // 传真
    private String email;           // 邮箱
    private String orgWebsite;      // 官网
    private String regAddressCn;    // 注册地址 (中文)
    private String officeAddressCn; // 办公地址 (中文)
    private String currency;        // 币种 (如: CNY)
    private String listedDate;      // 上市日期 (时间戳格式)
    private String provincialName;  // 所在省份 (如: 北京市)
    private String actualController; // 实际控制人 (含持股比例)
    private String classiName;      // 企业类型 (如: 民营企业)
    private String preNameCn;       // 曾用名 (中文)
    private String chairman;        // 董事长
    private Integer executivesNums; // 高管人数
    private BigDecimal actualIssueVol; // 实际发行数量
    private BigDecimal issuePrice;  // 发行价 (元)
    private BigDecimal actualRcNetAmt; // 实际募集净额 (元)
    private String affiliateIndustry;  // 所属板块/行业
    private Integer version;         // 版本号 (从0开始, 关键字段变化时+1)
    private LocalDateTime createdAt;  // 创建时间
    private LocalDateTime updatedAt;  // 更新时间
}
