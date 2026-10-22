package com.teachme.domain;

import java.util.UUID;

/**
 * 設定が完了した利用者の設定 (仕様書 5.1、概念モデル 2.1)。
 *
 * @param studentType 担当する AI 生徒の種類
 * @param subject 教科
 * @param unitId 現在の単元の識別子
 * @param unitName 現在の単元の名前
 * @param lessonsPerWeek 週の授業回数
 * @param privacyConfirmed 個人情報の確認のチェック。授業と相談の API は、これが true でなければ使えない (openapi.yaml の決まり)
 */
public record UserSettings(
    StudentType studentType,
    Subject subject,
    UUID unitId,
    String unitName,
    int lessonsPerWeek,
    boolean privacyConfirmed) {}
