package com.sky.service;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/*
 * ============================================================================
 *  员工登录业务 单元测试（EmployeeServiceImpl.login）
 * ----------------------------------------------------------------------------
 *  【这是什么】
 *  单元测试 = 只测"一个类/一个方法"的逻辑，不启动 Spring、不连数据库。
 *  登录逻辑依赖数据库（EmployeeMapper），我们用一个"假的 Mapper"(Mock) 代替它，
 *  让它"想返回什么就返回什么"，从而专注验证 login() 方法里的 if/else 分支对不对。
 *
 *  【关键注解，先看懂】
 *  @ExtendWith(MockitoExtension.class) —— 启用 Mockito 框架，让下面的 @Mock/@InjectMocks 生效。
 *  @Mock   EmployeeMapper  —— 造一个假的数据库访问对象，方法返回值由我们手动指定。
 *  @InjectMocks EmployeeServiceImpl —— 创建真实的被测 Service，并自动把上面的 @Mock 注入进去。
 *
 *  【测试结构：Given-When-Then（准备-执行-断言）】
 *  每个用例都分三段，这是业界通用写法：
 *    Given  准备输入数据 + 设置 Mock 的返回
 *    When   调用被测方法
 *    Then   断言结果/异常是否符合预期
 *
 *  【命名规范】should_预期_when_条件，配合 @DisplayName 写中文，IDE 里看得到中文名。
 * ============================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("员工登录逻辑测试")
class EmployeeServiceImplTest {

    // 假的数据库访问层：不真正查库，返回什么由每个用例自己控制
    @Mock
    private EmployeeMapper employeeMapper;

    // 被测对象：真实的 Service 实现，Mockito 会把上面的 employeeMapper 注入进来
    @InjectMocks
    private EmployeeServiceImpl employeeService;

    // 测试数据工厂：统一在这里造对象，避免每个用例重复 new（可读性更好）
    private EmployeeLoginDTO buildLoginDTO(String username, String password) {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private Employee buildEmployee(String password, Integer status) {
        return Employee.builder()
                .id(1L)
                .username("admin")
                .name("管理员")
                .password(password)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("登录失败：账号不存在时抛出 AccountNotFoundException")
    void should_throwAccountNotFound_when_usernameNotExists() {
        // Given：数据库查不到这个用户（getByUsername 返回 null）
        EmployeeLoginDTO dto = buildLoginDTO("ghost", "123456");
        when(employeeMapper.getByUsername("ghost")).thenReturn(null);

        // When & Then：调用 login，断言它抛出了"账号不存在"异常，且提示语正确
        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> employeeService.login(dto)
        );
        assertEquals(MessageConstant.ACCOUNT_NOT_FOUND, ex.getMessage());
    }

    @Test
    @DisplayName("登录失败：密码错误时抛出 PasswordErrorException")
    void should_throwPasswordError_when_passwordWrong() {
        // Given：用户存在，密码存的是 123456，但登录提交的是 wrong-password
        EmployeeLoginDTO dto = buildLoginDTO("admin", "wrong-password");
        Employee exist = buildEmployee("123456", StatusConstant.ENABLE);
        when(employeeMapper.getByUsername("admin")).thenReturn(exist);

        // When & Then
        PasswordErrorException ex = assertThrows(
                PasswordErrorException.class,
                () -> employeeService.login(dto)
        );
        assertEquals(MessageConstant.PASSWORD_ERROR, ex.getMessage());
    }

    @Test
    @DisplayName("登录失败：账号被禁用时抛出 AccountLockedException")
    void should_throwAccountLocked_when_statusIsDisabled() {
        // Given：用户存在、密码也对，但账号状态是"禁用"(DISABLE=0)
        EmployeeLoginDTO dto = buildLoginDTO("admin", "123456");
        Employee exist = buildEmployee("123456", StatusConstant.DISABLE);
        when(employeeMapper.getByUsername("admin")).thenReturn(exist);

        // When & Then
        AccountLockedException ex = assertThrows(
                AccountLockedException.class,
                () -> employeeService.login(dto)
        );
        assertEquals(MessageConstant.ACCOUNT_LOCKED, ex.getMessage());
    }

    @Test
    @DisplayName("登录成功：账号存在、密码正确、状态正常时返回员工对象")
    void should_returnEmployee_when_loginValid() {
        // Given：一切正常
        EmployeeLoginDTO dto = buildLoginDTO("admin", "123456");
        Employee exist = buildEmployee("123456", StatusConstant.ENABLE);
        when(employeeMapper.getByUsername("admin")).thenReturn(exist);

        // When
        Employee result = employeeService.login(dto);

        // Then：返回的就是数据库里那条员工数据
        assertNotNull(result);
        assertEquals("admin", result.getUsername());
        assertEquals(1L, result.getId());
    }
}
