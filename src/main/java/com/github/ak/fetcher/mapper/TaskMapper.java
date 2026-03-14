package com.github.ak.fetcher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.ak.fetcher.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
