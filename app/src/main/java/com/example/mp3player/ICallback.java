package com.example.mp3player;

public interface ICallback {
    void durationEvent(int duration);
    void progressEvent(int elapsedTime);
}