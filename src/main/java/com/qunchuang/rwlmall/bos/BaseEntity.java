package com.qunchuang.rwlmall.bos;

// Generated file. Do not edit.
// Generated by org.xdoclet.plugin.timbos.ActionScript3Plugin from com.tim.bos.BaseEntity

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

import javax.persistence.*;

@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Access(AccessType.FIELD)
public class BaseEntity extends CoreObject implements IEntity {

    @Column(name="number",unique=true,nullable=false,length=40)
    private String number;

    @CreatedBy
    @Column(name="createactorid",length=40)
    private String createactorid;

    @LastModifiedBy
    @Column(name="updateactorid",length=40)
    private String updateactorid;

    @LastModifiedDate
    private long updatetime;

    @CreatedDate
    private long createtime;

   /* @Column(name="objectstatus",length=25)
    @Enumerated(EnumType.STRING)
    private EntityStatus objectstatus;



    public EntityStatus getObjectstatus() {
        return objectstatus;
    }
    public void setObjectstatus(EntityStatus objectstatus) {
        this.objectstatus = objectstatus;
    }
*/

    public String getCreateactorid() {
        return createactorid;
    }
   /* public void setCreateactorid(String createactorid) {
        this.createactorid = createactorid;
    }
*/
    public String getUpdateactorid() {
        return updateactorid;
    }
    public long getUpdatetime() {
        return updatetime;
    }


    public void setNumber(String number) {
        this.number = number;
    }

    public String getNumber() {
        return number;
    }

    /*   public void setUpdatetime(long updatetime) {
           this.updatetime = updatetime;
       }*/
    public long getCreatetime() {
        return createtime;
    }
/*
    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }
*/

    //如果number没有,自动生成number
    @PrePersist
    public void prePersist(){
        //可以通过this  获取到当前的class 是什么实体     获取到之后可以作为redis的键值
        Class<? extends BaseEntity> aClass = this.getClass();
        aClass.toString();
        //todo 结合redis 让number的位数变为7 8位 2018年3月29日10:30:57
        if(!StringUtils.hasText(number)){
        this.updatetime= System.currentTimeMillis() ;
        this.number=String.valueOf(System.currentTimeMillis()).substring(1);
        }
    }

   /* @PreUpdate
    private void preUpdate(){
        this.updatetime=DatetimeUtils.getCurrentTime().getTime();
        if(!StringUtils.hasText(number)){
            this.number=""+DatetimeUtils.getCurrentTime().getTime();
        }
    }

*/

}
