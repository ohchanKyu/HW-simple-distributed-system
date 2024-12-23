package util;

public record RequestDto(String method, String url, String body) { }