package com.itheima.mapper;

import com.itheima.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper{

    @Insert("insert into test.user(name,age) values (#{name},#{age})")
    // 由于@CachePut注解需要用到插入数据的id，所以这里需要配置useGeneratedKeys = true,keyProperty = "id"
    @Options(useGeneratedKeys = true,keyProperty = "id")
    void insert(User user);

    @Delete("delete from test.user where id = #{id}")
    void deleteById(Long id);

    @Delete("delete from test.user")
    void deleteAll();

    @Select("select * from test.user where id = #{id}")
    User getById(Long id);
}
