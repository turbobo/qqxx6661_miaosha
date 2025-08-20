package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.TicketOrder;
import cn.monitor4all.miaoshadao.mapper.TicketOrderMapper;
import cn.monitor4all.miaoshadao.model.BusinessException;
import cn.monitor4all.miaoshadao.model.ErrorCode;
import cn.monitor4all.miaoshaservice.service.TicketOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 票券订单服务实现类
 */
@Service
public class TicketOrderServiceImpl implements TicketOrderService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketOrderServiceImpl.class);
    
    @Resource
    private TicketOrderMapper ticketOrderMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketOrder createTicketOrder(Long userId, Integer ticketId, String ticketCode, String ticketDate, Long amount) {
        LOGGER.info("开始创建票券订单，用户ID: {}, 票券ID: {}, 票券编码: {}, 日期: {}, 金额: {}", 
            userId, ticketId, ticketCode, ticketDate, amount);
        
        try {
            // 生成订单编号
            String orderNo = generateOrderNo();
            
            // 创建票券订单
            TicketOrder ticketOrder = new TicketOrder(orderNo, userId, ticketId, ticketCode, ticketDate, amount);
            
            // 插入数据库
            int insertResult = ticketOrderMapper.insert(ticketOrder);
            if (insertResult <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "票券订单创建失败");
            }
            
            LOGGER.info("票券订单创建成功，订单编号: {}, 用户ID: {}, 票券编码: {}", 
                orderNo, userId, ticketCode);
            
            return ticketOrder;
            
        } catch (Exception e) {
            LOGGER.error("创建票券订单失败，用户ID: {}, 票券编码: {}, 错误: {}", 
                userId, ticketCode, e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public TicketOrder getTicketOrderById(Integer id) {
        try {
            return ticketOrderMapper.selectByPrimaryKey(id);
        } catch (Exception e) {
            LOGGER.error("根据ID查询票券订单失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public TicketOrder getTicketOrderByOrderNo(String orderNo) {
        try {
            return ticketOrderMapper.selectByOrderNo(orderNo);
        } catch (Exception e) {
            LOGGER.error("根据订单编号查询票券订单失败，订单编号: {}, 错误: {}", orderNo, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public List<TicketOrder> getTicketOrdersByUserId(Long userId) {
        try {
            return ticketOrderMapper.selectByUserId(userId);
        } catch (Exception e) {
            LOGGER.error("根据用户ID查询票券订单列表失败，用户ID: {}, 错误: {}", userId, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public TicketOrder getTicketOrderByTicketCode(String ticketCode) {
        try {
            return ticketOrderMapper.selectByTicketCode(ticketCode);
        } catch (Exception e) {
            LOGGER.error("根据票券编码查询票券订单失败，票券编码: {}, 错误: {}", ticketCode, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public List<TicketOrder> getTicketOrdersByTicketId(Integer ticketId) {
        try {
            return ticketOrderMapper.selectByTicketId(ticketId);
        } catch (Exception e) {
            LOGGER.error("根据票券ID查询票券订单列表失败，票券ID: {}, 错误: {}", ticketId, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public List<TicketOrder> getTicketOrdersByStatus(Integer status) {
        try {
            return ticketOrderMapper.selectByStatus(status);
        } catch (Exception e) {
            LOGGER.error("根据状态查询票券订单列表失败，状态: {}, 错误: {}", status, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public List<TicketOrder> getTicketOrdersByUserIdAndStatus(Long userId, Integer status) {
        try {
            return ticketOrderMapper.selectByUserIdAndStatus(userId, status);
        } catch (Exception e) {
            LOGGER.error("根据用户ID和状态查询票券订单列表失败，用户ID: {}, 状态: {}, 错误: {}", 
                userId, status, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTicketOrderStatus(String orderNo, Integer status) {
        LOGGER.info("开始更新票券订单状态，订单编号: {}, 新状态: {}", orderNo, status);
        
        try {
            TicketOrder ticketOrder = ticketOrderMapper.selectByOrderNo(orderNo);
            if (ticketOrder == null) {
                LOGGER.warn("票券订单不存在，订单编号: {}", orderNo);
                return false;
            }
            
            ticketOrder.setStatus(status);
            ticketOrder.setUpdateTime(new Date());
            
            int updateResult = ticketOrderMapper.updateByPrimaryKey(ticketOrder);
            if (updateResult <= 0) {
                LOGGER.error("更新票券订单状态失败，订单编号: {}", orderNo);
                return false;
            }
            
            LOGGER.info("票券订单状态更新成功，订单编号: {}, 新状态: {}", orderNo, status);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("更新票券订单状态失败，订单编号: {}, 新状态: {}, 错误: {}", 
                orderNo, status, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payTicketOrder(String orderNo) {
        LOGGER.info("开始支付票券订单，订单编号: {}", orderNo);
        
        try {
            TicketOrder ticketOrder = ticketOrderMapper.selectByOrderNo(orderNo);
            if (ticketOrder == null) {
                LOGGER.warn("票券订单不存在，订单编号: {}", orderNo);
                return false;
            }
            
            if (ticketOrder.getStatus() != 1) {
                LOGGER.warn("票券订单状态不正确，订单编号: {}, 当前状态: {}", orderNo, ticketOrder.getStatus());
                return false;
            }
            
            ticketOrder.setStatus(2); // 已支付
            ticketOrder.setPayTime(new Date());
            ticketOrder.setUpdateTime(new Date());
            
            int updateResult = ticketOrderMapper.updateByPrimaryKey(ticketOrder);
            if (updateResult <= 0) {
                LOGGER.error("支付票券订单失败，订单编号: {}", orderNo);
                return false;
            }
            
            LOGGER.info("票券订单支付成功，订单编号: {}", orderNo);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("支付票券订单失败，订单编号: {}, 错误: {}", orderNo, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTicketOrder(String orderNo) {
        LOGGER.info("开始取消票券订单，订单编号: {}", orderNo);
        
        try {
            TicketOrder ticketOrder = ticketOrderMapper.selectByOrderNo(orderNo);
            if (ticketOrder == null) {
                LOGGER.warn("票券订单不存在，订单编号: {}", orderNo);
                return false;
            }
            
            if (ticketOrder.getStatus() != 1) {
                LOGGER.warn("票券订单状态不正确，订单编号: {}, 当前状态: {}", orderNo, ticketOrder.getStatus());
                return false;
            }
            
            ticketOrder.setStatus(3); // 已取消
            ticketOrder.setUpdateTime(new Date());
            
            int updateResult = ticketOrderMapper.updateByPrimaryKey(ticketOrder);
            if (updateResult <= 0) {
                LOGGER.error("取消票券订单失败，订单编号: {}", orderNo);
                return false;
            }
            
            LOGGER.info("票券订单取消成功，订单编号: {}", orderNo);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("取消票券订单失败，订单编号: {}, 错误: {}", orderNo, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTicketOrderById(Integer id) {
        LOGGER.info("开始删除票券订单，ID: {}", id);
        
        try {
            int deleteResult = ticketOrderMapper.deleteByPrimaryKey(id);
            if (deleteResult <= 0) {
                LOGGER.warn("票券订单不存在，ID: {}", id);
                return false;
            }
            
            LOGGER.info("票券订单删除成功，ID: {}", id);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("删除票券订单失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTicketOrderByOrderNo(String orderNo) {
        LOGGER.info("开始删除票券订单，订单编号: {}", orderNo);
        
        try {
            int deleteResult = ticketOrderMapper.deleteByOrderNo(orderNo);
            if (deleteResult <= 0) {
                LOGGER.warn("票券订单不存在，订单编号: {}", orderNo);
                return false;
            }
            
            LOGGER.info("票券订单删除成功，订单编号: {}", orderNo);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("删除票券订单失败，订单编号: {}, 错误: {}", orderNo, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 生成订单编号
     * 格式：TO + 时间戳 + 4位随机数
     *
     * @return 订单编号
     */
    private String generateOrderNo() {
        long timestamp = System.currentTimeMillis();
        int randomNum = (int) (Math.random() * 10000);
        return "TO" + timestamp + String.format("%04d", randomNum);
    }
}
