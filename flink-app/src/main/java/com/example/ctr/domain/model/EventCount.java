package com.example.ctr.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이벤트 집계 결과를 담는 도메인 모델
 * - impressions: 조회수 (view 이벤트 카운트)
 * - clicks: 클릭수 (click 이벤트 카운트)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventCount {

    /**
     * 조회수 (view 이벤트 카운트)
     */
    private long impressions;

    /**
     * 클릭수 (click 이벤트 카운트)
     */
    private long clicks;

    /**
     * 초기 상태의 EventCount 생성 (모든 카운트가 0)
     */
    public static EventCount initial() {
        return new EventCount(0L, 0L);
    }

    /**
     * view 이벤트 카운트 증가
     */
    public void incrementImpressions() {
        this.impressions++;
    }

    /**
     * click 이벤트 카운트 증가
     */
    public void incrementClicks() {
        this.clicks++;
    }

    /**
     * 두 개의 EventCount를 병합
     */
    public static EventCount merge(EventCount a, EventCount b) {
        return new EventCount(
                a.impressions + b.impressions,
                a.clicks + b.clicks);
    }

    /**
     * 현재 EventCount의 복사본 생성
     */
    public EventCount copy() {
        return new EventCount(this.impressions, this.clicks);
    }
}
