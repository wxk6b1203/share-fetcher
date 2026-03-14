package com.github.ak.fetcher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.ak.fetcher.entity.StockFundamental;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StockFundamentalMapper extends BaseMapper<StockFundamental> {

    @Select("SELECT * FROM stock_fundamental WHERE code = #{code}")
    StockFundamental selectByCode(String code);

    @Select("SELECT * FROM stock_fundamental WHERE code = #{code} ORDER BY version DESC LIMIT 1")
    StockFundamental selectLatestByCode(String code);
}
