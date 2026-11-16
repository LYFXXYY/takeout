package com.sky.service;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.service.impl.DishServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * ============================================================================
 *  菜品删除业务 单元测试（DishServiceImpl.deleteBatch）
 * ----------------------------------------------------------------------------
 *  【被测业务规则】删除菜品前有两道校验：
 *    1) 只要有一个菜品处于"起售中"(status=1)，就不允许删除 → 抛 DeletionNotAllowedException
 *    2) 如果这些菜品被"套餐"关联引用了，也不允许删除        → 抛 DeletionNotAllowedException
 *    两条都通过后，才真正删除 菜品表 + 口味表。
 *
 *  【这个类为什么适合做 Mock 练习】
 *  它自己不碰数据库，所有查询/删除都委托给 4 个 Mapper。我们只需 Mock 这些 Mapper 的返回，
 *  就能构造出"起售中""被关联""可删除"这三种场景，非常典型。
 *
 *  【verify(...) 的用法】
 *  除了验证返回值，单元测试还常验证"某个方法到底有没有被调用、调用了几次"。
 *  比如校验失败时，应该"从未调用"真正的删除方法(dishMapper.deleteByIds)，用 never() 断言。
 * ============================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("菜品删除逻辑测试")
class DishServiceImplTest {

    // DishServiceImpl 内部有这 4 个依赖，全部 Mock 掉；@InjectMocks 会按类型注入
    @Mock private DishMapper dishMapper;
    @Mock private DishFlavorMapper dishFlavorMapper;
    @Mock private SetmealMapper setmealMapper;
    @Mock private SetmealDishMapper setmealDishMapper;

    @InjectMocks
    private DishServiceImpl dishService;

    // 待删除的菜品 id 列表，多个用例复用
    private List<Long> ids;

    @BeforeEach
    void setUp() {
        ids = Collections.singletonList(10L);
    }

    @Test
    @DisplayName("删除失败：存在起售中的菜品时不允许删除")
    void should_throwWhenOnSale_when_deleteOnSaleDish() {
        // Given：id=10 的菜品状态是"起售中"(ENABLE=1)
        Dish onSale = Dish.builder().id(10L).status(StatusConstant.ENABLE).build();
        when(dishMapper.getById(10L)).thenReturn(onSale);

        // When & Then：应抛异常
        DeletionNotAllowedException ex = assertThrows(
                DeletionNotAllowedException.class,
                () -> dishService.deleteBatch(ids)
        );
        assertEquals(MessageConstant.DISH_ON_SALE, ex.getMessage());

        // 并且：因为校验没通过，真正的删除动作绝不能被执行
        verify(dishMapper, never()).deleteByIds(ids);
        verify(dishFlavorMapper, never()).deleteByDishIds(ids);
    }

    @Test
    @DisplayName("删除失败：菜品被套餐关联时不允许删除")
    void should_throwWhenRelated_when_deleteDishUsedBySetmeal() {
        // Given：菜品是"停售"(可删)，但查到它被套餐关联了（getSetmealIdsByDishIds 返回非空）
        Dish stopped = Dish.builder().id(10L).status(StatusConstant.DISABLE).build();
        when(dishMapper.getById(10L)).thenReturn(stopped);
        when(setmealDishMapper.getSetmealIdsByDishIds(ids))
                .thenReturn(Arrays.asList(99L)); // 非空 => 被套餐引用

        // When & Then
        DeletionNotAllowedException ex = assertThrows(
                DeletionNotAllowedException.class,
                () -> dishService.deleteBatch(ids)
        );
        assertEquals(MessageConstant.DISH_BE_RELATED_BY_SETMEAL, ex.getMessage());
        verify(dishMapper, never()).deleteByIds(ids);
    }

    @Test
    @DisplayName("删除成功：菜品停售且未被套餐关联时，删除菜品表和口味表")
    void should_deleteBothTables_when_deleteValidDish() {
        // Given：停售 + 未被任何套餐关联
        Dish stopped = Dish.builder().id(10L).status(StatusConstant.DISABLE).build();
        when(dishMapper.getById(10L)).thenReturn(stopped);
        when(setmealDishMapper.getSetmealIdsByDishIds(ids))
                .thenReturn(Collections.emptyList()); // 空 => 未被关联

        // When
        dishService.deleteBatch(ids);

        // Then：两条删除语句都应各被调用一次
        verify(dishMapper, times(1)).deleteByIds(ids);
        verify(dishFlavorMapper, times(1)).deleteByDishIds(ids);
    }
}
