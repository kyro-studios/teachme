package com.teachme.ai;

/**
 * 画像や音声などのバイナリ。`mimeType` は "image/jpeg" や "audio/webm" などの形式。
 *
 * @param mimeType 形式
 * @param bytes 中身
 */
public record Media(String mimeType, byte[] bytes) {}
