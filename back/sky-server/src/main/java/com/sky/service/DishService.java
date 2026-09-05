package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {
    //新增菜品和对应口味
    public void saveWithFlavor(DishDTO dishDTO);
    //菜品查询
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);
    // 菜品删除
    void deleteBatch(List<Long> ids);
    //根据id查询菜品
    DishVO getByIdWithFlavor(Long id);
    //编辑菜品
    void updateWithFlavor(DishDTO dishDTO);
    //查询菜品口味
    List<DishVO> listWithFlavor(Dish dish);

    List<Dish> list(Long categoryId);

    /**
     * 菜品起售停售
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);
}