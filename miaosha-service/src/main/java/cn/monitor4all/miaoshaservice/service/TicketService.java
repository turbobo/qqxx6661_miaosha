package cn.monitor4all.miaoshaservice.service;


import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshadao.model.Ticket;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TicketService {
    // 存储票券信息，key为日期
    private Map<LocalDate, Ticket> tickets = new ConcurrentHashMap<>();
    
    // 存储购买记录，key为用户ID
    private Map<String, List<PurchaseRecord>> purchaseRecords = new ConcurrentHashMap<>();
    
    public TicketService() {
        // 初始化最近3天的票券数据
        LocalDate today = LocalDate.now();
        tickets.put(today, new Ticket(today, 100));
        tickets.put(today.plusDays(1), new Ticket(today.plusDays(1), 150));
        tickets.put(today.plusDays(2), new Ticket(today.plusDays(2), 200));
    }
    
    // 获取最近3天的票券信息
    public List<Ticket> getRecentTickets() {
        LocalDate today = LocalDate.now();
        return Arrays.asList(
            tickets.get(today),
            tickets.get(today.plusDays(1)),
            tickets.get(today.plusDays(2))
        );
    }
    
    // 购买票券
    public synchronized PurchaseRecord purchaseTicket(String userId, LocalDate date) {
        // 检查是否已经购买
        if (hasPurchased(userId, date)) {
            return null;
        }
        
        // 获取当天票券
        Ticket ticket = tickets.get(date);
        if (ticket == null) {
            return null;
        }
        
        // 尝试购买
        boolean success = ticket.purchase();
        if (!success) {
            return null;
        }
        
        // 生成票券编号
        String ticketCode = generateTicketCode(userId, date);
        
        // 记录购买信息
        PurchaseRecord record = new PurchaseRecord(userId, date, ticketCode);
        purchaseRecords.computeIfAbsent(userId, k -> new ArrayList<>()).add(record);
        
        return record;
    }
    
    // 检查用户是否已购买当天票券
    public boolean hasPurchased(String userId, LocalDate date) {
        List<PurchaseRecord> records = purchaseRecords.get(userId);
        if (records == null) {
            return false;
        }
        
        return records.stream()
            .anyMatch(record -> record.getDate().equals(date));
    }
    
    // 生成票券编号
    private String generateTicketCode(String userId, LocalDate date) {
        String dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String userHash = Integer.toHexString(userId.hashCode()).substring(0, 4);
        String randomStr = UUID.randomUUID().toString().substring(0, 6);
        return "T" + dateStr + userHash + randomStr.toUpperCase();
    }
}
