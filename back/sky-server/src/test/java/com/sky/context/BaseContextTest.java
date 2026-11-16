package com.sky.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * ============================================================================
 *  线程上下文 单元测试（BaseContext）
 * ----------------------------------------------------------------------------
 *  【这是什么】
 *  BaseContext 用一个 ThreadLocal<Long> 存"当前登录用户 id"。
 *  ThreadLocal 的特点是：每个线程各存各的副本，互不干扰。
 *  拦截器在请求进来时 setCurrentId，业务代码随时 getCurrentId 取值。
 *
 *  【为什么测它】
 *  它简单，但能非常直观地帮你理解 ThreadLocal——尤其"线程隔离"这个最容易被误解的点。
 *
 *  【@AfterEach 的作用】
 *  每个用例跑完后都会执行 removeCurrentId()，清理 ThreadLocal，
 *  防止一个用例设置的值"污染"到下一个用例（测试要相互独立）。
 * ============================================================================
 */
@DisplayName("线程上下文 BaseContext 测试")
class BaseContextTest {

    // 后置清理：每个测试方法结束后都执行，保证用例之间互不影响
    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    @DisplayName("同一线程内：set 之后能 get 到同一个值")
    void should_returnSameId_when_setInSameThread() {
        // Given & When
        BaseContext.setCurrentId(1001L);

        // Then
        assertEquals(1001L, BaseContext.getCurrentId());
    }

    @Test
    @DisplayName("remove 之后：get 返回 null")
    void should_returnNull_when_removed() {
        // Given
        BaseContext.setCurrentId(1001L);

        // When
        BaseContext.removeCurrentId();

        // Then
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    @DisplayName("线程隔离：新线程看不到主线程设置的值（ThreadLocal 的核心特性）")
    void should_isolatePerThread_when_accessedFromAnotherThread() throws InterruptedException {
        // Given：在主线程设置一个值
        BaseContext.setCurrentId(1001L);

        // 用一个数组当"信使"，把子线程里读到的值带回来做断言
        final Long[] valueSeenInOtherThread = new Long[1];

        // When：另起一个线程去读
        Thread t = new Thread(() -> valueSeenInOtherThread[0] = BaseContext.getCurrentId());
        t.start();
        t.join(); // 等子线程跑完

        // Then：主线程的值不会泄漏到子线程，子线程读到的是 null
        assertNull(valueSeenInOtherThread[0]);
        // 而主线程自己的值还在
        assertEquals(1001L, BaseContext.getCurrentId());
    }
}
