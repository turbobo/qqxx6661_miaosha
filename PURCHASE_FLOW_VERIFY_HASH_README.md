# 抢购流程验证机制说明

## 🎯 概述

为了实现更安全的抢购机制，系统采用了双重验证流程：先获取验证值`verifyHash`，再携带验证值进行抢购。这种方式可以有效防止恶意请求和重复提交。

## 🔄 新的抢购流程

### 1. 用户点击抢购按钮
- 显示确认弹窗
- 用户确认后进入抢购流程

### 2. 第一步：获取验证值
- **接口**: `GET /api/tickets/getVerifyHash`
- **参数**: 
  - `userId`: 用户ID (Long类型，默认-1表示匿名用户)
  - `date`: 票券日期
- **返回**: 验证值`verifyHash`

### 3. 第二步：执行抢购
- **接口**: `POST /api/tickets/v1/purchase`
- **参数**:
  - `userId`: 用户ID
  - `date`: 票券日期
  - `verifyHash`: 第一步获取的验证值
- **返回**: 抢购结果

## 🏗️ 前端实现

### 1. 状态管理
```javascript
data() {
    return {
        // ... 其他状态
        isPurchasing: false // 抢购进行中状态
    }
}
```

### 2. 抢购流程实现
```javascript
async confirmPurchase() {
    try {
        // 设置抢购状态
        this.isPurchasing = true;
        
        // 第一步：获取验证值
        const verifyResponse = await axios.get('/api/tickets/getVerifyHash', {
            params: { userId: this.currentUserId, date: this.selectedTicket.date }
        });
        
        if (verifyResponse.data.code !== 200) {
            throw new Error('获取验证值失败');
        }
        
        const verifyHash = verifyResponse.data.data;
        
        // 第二步：执行抢购
        const response = await axios.post('/api/tickets/v1/purchase', {
            userId: this.currentUserId,
            date: this.selectedTicket.date,
            verifyHash: verifyHash
        });
        
        // 处理抢购结果
        if (response.data.code === 200) {
            // 抢购成功
            this.handlePurchaseSuccess(response.data.data);
        } else {
            // 抢购失败
            this.handlePurchaseFailure(response.data.message);
        }
        
    } catch (err) {
        // 错误处理
        this.handlePurchaseError(err);
    } finally {
        // 重置抢购状态
        this.isPurchasing = false;
    }
}
```

### 3. UI状态控制
- **抢购按钮**: 在抢购过程中显示"抢购中..."并禁用
- **加载指示器**: 显示旋转图标和进度提示
- **状态提示**: 页面顶部显示抢购进行中的状态

## 🛡️ 安全机制

### 1. 双重验证
- **第一步验证**: 获取`verifyHash`，验证用户身份和请求合法性
- **第二步验证**: 使用`verifyHash`执行抢购，防止重复提交

### 2. 防重复提交
- 抢购过程中禁用按钮
- 使用`verifyHash`确保每次请求的唯一性
- 后端验证`verifyHash`的有效性

### 3. 错误处理
- 网络错误分类处理
- 服务器错误状态码处理
- 用户友好的错误提示

## 📱 用户体验优化

### 1. 视觉反馈
- **按钮状态**: 抢购中显示加载图标和"抢购中..."文字
- **进度提示**: 页面顶部显示抢购状态
- **禁用状态**: 抢购过程中禁用所有相关操作

### 2. 错误提示
- **网络错误**: "网络连接失败，请检查网络后重试"
- **参数错误**: "请求参数错误"
- **服务器错误**: "服务器内部错误，请稍后重试"
- **业务错误**: 显示具体的业务错误信息

### 3. 日志记录
- 前端记录关键操作日志
- 便于问题排查和用户行为分析

## 🔧 技术实现细节

### 1. 异步流程控制
```javascript
// 使用 async/await 确保流程顺序
const verifyHash = await getVerifyHash();
const result = await purchaseWithVerifyHash(verifyHash);
```

### 2. 状态管理
```javascript
// 抢购状态控制
this.isPurchasing = true;  // 开始抢购
try {
    // 抢购逻辑
} finally {
    this.isPurchasing = false;  // 结束抢购
}
```

### 3. 错误边界处理
```javascript
try {
    // 抢购逻辑
} catch (err) {
    if (err.response) {
        // HTTP错误处理
    } else if (err.request) {
        // 网络错误处理
    } else {
        // 其他错误处理
    }
}
```

## 📊 流程时序图

```
用户点击抢购 → 显示确认弹窗 → 用户确认
    ↓
设置抢购状态(isPurchasing = true)
    ↓
调用 getVerifyHash 接口
    ↓
获取验证值 verifyHash
    ↓
调用 purchase 接口(携带verifyHash)
    ↓
处理抢购结果
    ↓
重置抢购状态(isPurchasing = false)
    ↓
显示结果弹窗(成功/失败)
```

## 🧪 测试验证

### 1. 正常流程测试
- 验证获取验证值成功
- 验证携带验证值抢购成功
- 验证状态切换正确

### 2. 异常流程测试
- 验证获取验证值失败的处理
- 验证网络错误的处理
- 验证服务器错误的处理

### 3. 并发测试
- 验证多个用户同时抢购
- 验证同一用户重复点击的处理

## 📝 总结

新的抢购流程通过以下方式提升了系统的安全性和用户体验：

✅ **安全性提升**: 双重验证机制，防止恶意请求  
✅ **用户体验**: 清晰的状态提示和错误处理  
✅ **系统稳定性**: 完善的异常处理和状态管理  
✅ **可维护性**: 清晰的代码结构和日志记录  

这种设计既保证了抢购的安全性，又提供了良好的用户体验。🎉
