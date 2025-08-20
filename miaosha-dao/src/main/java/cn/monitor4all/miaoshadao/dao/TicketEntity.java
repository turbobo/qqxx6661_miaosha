package cn.monitor4all.miaoshadao.dao;

import java.util.Date;

/**
 * 票券数据库实体类
 */
public class TicketEntity {
    private Integer id;
    private String date;  // 改为String类型
    private String name;
    private Integer totalCount;
    private Integer remainingCount;
    private Integer soldCount;
    private Integer version;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    
    // 构造函数
    public TicketEntity() {}
    
    public TicketEntity(String date, String name, Integer totalCount) {
        this.date = date;
        this.name = name;
        this.totalCount = totalCount;
        this.remainingCount = totalCount;
        this.soldCount = 0;
        this.version = 1;
        this.status = 1;
    }
    
    // getter和setter方法
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getDate() {  // 改为String类型
        return date;
    }
    
    public void setDate(String date) {  // 改为String类型
        this.date = date;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Integer getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
    
    public Integer getRemainingCount() {
        return remainingCount;
    }
    
    public void setRemainingCount(Integer remainingCount) {
        this.remainingCount = remainingCount;
    }
    
    public Integer getSoldCount() {
        return soldCount;
    }
    
    public void setSoldCount(Integer soldCount) {
        this.soldCount = soldCount;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
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
    
    @Override
    public String toString() {
        return "TicketEntity{" +
                "id=" + id +
                ", date='" + date + '\'' +
                ", name='" + name + '\'' +
                ", totalCount=" + totalCount +
                ", remainingCount=" + remainingCount +
                ", soldCount=" + soldCount +
                ", version=" + version +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}