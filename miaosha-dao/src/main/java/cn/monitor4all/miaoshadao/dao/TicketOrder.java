package cn.monitor4all.miaoshadao.dao;

import java.util.Date;

/**
 * 票券订单实体类
 */
public class TicketOrder {
    
    /**
     * 主键ID
     */
    private Integer id;
    
    /**
     * 订单编号
     */
    private String orderNo;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 票券ID
     */
    private Integer ticketId;
    
    /**
     * 票券编码
     */
    private String ticketCode;
    
    /**
     * 购票日期
     */
    private String ticketDate;
    
    /**
     * 订单状态：1-待支付，2-已支付，3-已取消，4-已过期
     */
    private Integer status;
    
    /**
     * 订单金额（分）
     */
    private Long amount;
    
    /**
     * 支付时间
     */
    private Date payTime;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
    
    /**
     * 备注
     */
    private String remark;

    // 构造函数
    public TicketOrder() {}

    public TicketOrder(String orderNo, Long userId, Integer ticketId, String ticketCode, String ticketDate, Long amount) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.ticketId = ticketId;
        this.ticketCode = ticketCode;
        this.ticketDate = ticketDate;
        this.amount = amount;
        this.status = 1; // 默认待支付
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    // Getter和Setter方法
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public String getTicketDate() {
        return ticketDate;
    }

    public void setTicketDate(String ticketDate) {
        this.ticketDate = ticketDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Date getPayTime() {
        return payTime;
    }

    public void setPayTime(Date payTime) {
        this.payTime = payTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "TicketOrder{" +
                "id=" + id +
                ", orderNo='" + orderNo + '\'' +
                ", userId=" + userId +
                ", ticketId=" + ticketId +
                ", ticketCode='" + ticketCode + '\'' +
                ", ticketDate='" + ticketDate + '\'' +
                ", status=" + status +
                ", amount=" + amount +
                ", payTime=" + payTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", remark='" + remark + '\'' +
                '}';
    }
}
