package com.gopair.messageservice.domain.event;

import com.gopair.messageservice.domain.vo.MessageVO;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.context.ApplicationEvent;

/**
 * 消息发送事件
 * 
 * @author gopair
 */
@Data
@Accessors(chain = true)
public class MessageSentEvent extends ApplicationEvent {

    /**
     * 消息内容
     */
    private MessageVO messageVO;

    /**
     * 房间ID
     */
    private Long roomId;

    public MessageSentEvent(Object source, MessageVO messageVO, Long roomId) {
        super(source);
        this.messageVO = messageVO;
        this.roomId = roomId;
    }
} 