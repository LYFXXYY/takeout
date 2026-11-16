package com.sky.service;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.service.impl.SetmealServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * ============================================================================
 *  套餐删除 / 套餐启售 业务 单元测试（SetmealServiceImpl）
 * ----------------------------------------------------------------------------
 *  覆盖两个方法、四种分支：
 *    deleteBatch：① 起售中不可删  ② 停售时可删（删套餐表 + 删套餐菜品关联表）
 *    startOrStop：③ 启售时若套餐内含"停售的菜品"→ 启售失败  ④ 全部在售 → 启售成功
 *
 *  【观察点】
 *  - 校验失败时，用 verify(..., never()) 断言"更新/删除操作没被执行"，
 *    这是防止"该拒绝的没拒绝、还误改了数据库"的重要断言。
 *  - 校验成功时，用 verify(..., times(1)) 断言"该做的事确实做了"。
 *  - update() 的参数是方法内部 new 出来的对象，我们拿不到引用，
 *    所以用 any(Setmeal.class) 这个"匹配任意 Setmeal 参数"的匹配器来验证。
 * ============================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("套餐删除与启售逻辑测试")
class SetmealServiceImplTest {

    @Mock private SetmealMapper setmealMapper;
    @Mock private SetmealDishMapper setmealDishMapper;
    @Mock private DishMapper dishMapper;

    @InjectMocks
    private SetmealServiceImpl setmealService;

    // ---------------- deleteBatch ----------------

    @Test
    @DisplayName("删除失败：套餐处于起售中时不允许删除")
    void should_throwOnSale_when_deleteSellingSetmeal() {
        // Given：id=5 的套餐正在起售
        List<Long> ids = Collections.singletonList(5L);
        Setmeal onSale = Setmeal.builder().id(5L).status(StatusConstant.ENABLE).build();
        when(setmealMapper.getById(5L)).thenReturn(onSale);

        // When & Then
        DeletionNotAllowedException ex = assertThrows(
                DeletionNotAllowedException.class,
                () -> setmealService.deleteBatch(ids)
        );
        assertEquals(MessageConstant.SETMEAL_ON_SALE, ex.getMessage());
        verify(setmealMapper, never()).deleteById(any());
        verify(setmealDishMapper, never()).deleteBySetmealId(any());
    }

    @Test
    @DisplayName("删除成功：套餐停售时删除套餐表并清理套餐菜品关联")
    void should_deleteBothTables_when_deleteStoppedSetmeal() {
        // Given：id=5 的套餐已停售，可以删
        List<Long> ids = Collections.singletonList(5L);
        Setmeal stopped = Setmeal.builder().id(5L).status(StatusConstant.DISABLE).build();
        when(setmealMapper.getById(5L)).thenReturn(stopped);

        // When
        setmealService.deleteBatch(ids);

        // Then：删套餐 + 删关联，各一次
        verify(setmealMapper, times(1)).deleteById(5L);
        verify(setmealDishMapper, times(1)).deleteBySetmealId(5L);
    }

    // ---------------- startOrStop ----------------

    @Test
    @DisplayName("启售失败：套餐内含停售菜品时不允许启售")
    void should_throwEnableFailed_when_containsStoppedDish() {
        // Given：要把 id=5 的套餐设为"起售"(status=1)，但套餐里有一道菜是停售的
        List<Dish> dishes = Arrays.asList(
                Dish.builder().id(1L).status(StatusConstant.ENABLE).build(),
                Dish.builder().id(2L).status(StatusConstant.DISABLE).build() // 有停售的
        );
        when(dishMapper.getBySetmealId(5L)).thenReturn(dishes);

        // When & Then
        SetmealEnableFailedException ex = assertThrows(
                SetmealEnableFailedException.class,
                () -> setmealService.startOrStop(StatusConstant.ENABLE, 5L)
        );
        assertEquals(MessageConstant.SETMEAL_ENABLE_FAILED, ex.getMessage());
        verify(setmealMapper, never()).update(any(Setmeal.class));
    }

    @Test
    @DisplayName("启售成功：套餐内菜品全部在售时更新状态")
    void should_updateStatus_when_allDishesOnSale() {
        // Given：套餐内所有菜品都在售
        List<Dish> dishes = Arrays.asList(
                Dish.builder().id(1L).status(StatusConstant.ENABLE).build(),
                Dish.builder().id(2L).status(StatusConstant.ENABLE).build()
        );
        when(dishMapper.getBySetmealId(5L)).thenReturn(dishes);

        // When
        setmealService.startOrStop(StatusConstant.ENABLE, 5L);

        // Then：应调用一次 update 把状态写库
        verify(setmealMapper, times(1)).update(any(Setmeal.class));
    }
}
