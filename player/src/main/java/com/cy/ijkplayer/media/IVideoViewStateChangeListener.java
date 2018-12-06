package com.cy.ijkplayer.media;

/**
 * Created by shaoheyi on 2016/9/20.
 */
public interface IVideoViewStateChangeListener {

    /**
     * 目前报告的状态是不全的，只包含了由start,pause,resume方法导致的状态<br>
     * start方法：STATE_PLAYING<br>
     * pause方法：STATE_PAUSED<br>
     * resume方法：STATE_RESUME或者STATE_PREPARING<br>
     *
     * @param state
     */
    public void doOnVideoViewStateChange(int state);
}
