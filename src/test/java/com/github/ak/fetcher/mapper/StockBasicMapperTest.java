package com.github.ak.fetcher.mapper;

import com.github.ak.fetcher.entity.StockBasic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockBasicMapperTest {

    @Autowired
    private StockBasicMapper stockBasicMapper;

    @Test
    void testInsertAndSelect() {
        StockBasic stock = new StockBasic();
        stock.setCode("000001");
        stock.setName("平安银行");
        stock.setMarket("深交所");

        int result = stockBasicMapper.insert(stock);
        assertEquals(1, result);

        StockBasic found = stockBasicMapper.selectById("000001");
        assertNotNull(found);
        assertEquals("平安银行", found.getName());
    }
}
