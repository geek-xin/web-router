package com.geek.webrouter.web.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本比较器 — 只依赖 JDK，支持 {@code v} 前缀与预发布后缀。
 *
 * <p>比较规则：先按数字段逐段比较（{@code 1.10.0 > 1.9.0}）；数字段相同时，
 * 无预发布后缀的正式版大于带后缀的预发布版；都带后缀时按标识符逐个比较，
 * 纯数字标识符按数值比较且小于字母标识符。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public final class ReleaseVersionComparator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)(?:[-+](.*))?$");

    private ReleaseVersionComparator() {
    }

    /** 判断 {@code candidate} 是否比 {@code current} 更新。 */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    /** 比较两个版本号，返回值语义与 {@link Comparable#compareTo} 一致。 */
    public static int compare(String left, String right) {
        ParsedVersion a = parse(left);
        ParsedVersion b = parse(right);
        int length = Math.max(a.numbers().size(), b.numbers().size());
        for (int i = 0; i < length; i++) {
            int compared = Integer.compare(numberAt(a.numbers(), i), numberAt(b.numbers(), i));
            if (compared != 0) {
                return compared;
            }
        }
        return comparePrerelease(a.prerelease(), b.prerelease());
    }

    /** 去掉前缀 v/V 与空白。 */
    public static String normalize(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int numberAt(List<Integer> numbers, int index) {
        return index < numbers.size() ? numbers.get(index) : 0;
    }

    private static int comparePrerelease(String left, String right) {
        boolean leftEmpty = left == null || left.isEmpty();
        boolean rightEmpty = right == null || right.isEmpty();
        if (leftEmpty && rightEmpty) {
            return 0;
        }
        if (leftEmpty) {
            return 1;
        }
        if (rightEmpty) {
            return -1;
        }
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            if (i >= leftParts.length) {
                return -1;
            }
            if (i >= rightParts.length) {
                return 1;
            }
            int compared = compareIdentifier(leftParts[i], rightParts[i]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        }
        if (leftNumeric) {
            return -1;
        }
        if (rightNumeric) {
            return 1;
        }
        return left.toLowerCase(Locale.ROOT).compareTo(right.toLowerCase(Locale.ROOT));
    }

    private static ParsedVersion parse(String version) {
        String normalized = normalize(version);
        Matcher matcher = VERSION_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return new ParsedVersion(List.of(), normalized.isEmpty() ? null : normalized);
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : matcher.group(1).split("\\.")) {
            try {
                numbers.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                numbers.add(0);
            }
        }
        return new ParsedVersion(numbers, matcher.group(2));
    }

    private record ParsedVersion(List<Integer> numbers, String prerelease) {
    }
}
