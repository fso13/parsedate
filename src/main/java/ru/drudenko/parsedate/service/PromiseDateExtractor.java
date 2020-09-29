package ru.drudenko.parsedate.service;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.drudenko.parsedate.dto.PromiseDate;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.Temporal;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.time.temporal.TemporalAdjusters.firstInMonth;
import static java.time.temporal.TemporalAdjusters.lastInMonth;

/**
 * @author dmitrijrudenko
 */
@Component
public class PromiseDateExtractor {
    private static final String DIGITAL_YEAR_REGEXP = "2(?:11[0-3]|0[1-9]\\d|10\\d)";
    private static final Set<String> ACCURATE_YEAR_EXPR = new ImmutableSet.Builder<String>().add("the\\s+year\\s+" + DIGITAL_YEAR_REGEXP, "the\\s+year\\s+of\\s+" + DIGITAL_YEAR_REGEXP, "year\\s+" + DIGITAL_YEAR_REGEXP, "year\\s+of\\s+" + DIGITAL_YEAR_REGEXP, DIGITAL_YEAR_REGEXP + "\\s+year", "the\\s+" + DIGITAL_YEAR_REGEXP, DIGITAL_YEAR_REGEXP).build();
    private static final Set<String> CURRENT_YEAR_EXPR = new ImmutableSet.Builder<String>().add("the\\s+this", "the\\s+current", "this", "current").build();
    private static final Set<String> PREVIOUS_YEAR_EXPR = new ImmutableSet.Builder<String>().add("the\\s+last", "the\\s+previous", "last", "previous").build();
    private static final Set<String> NEXT_YEAR_EXPR = new ImmutableSet.Builder<String>().add("the\\s+upcoming", "the\\s+coming", "the\\s+next", "upcoming", "coming", "next").build();
    private static final Set<String> SAME_YEAR_EXPR = new ImmutableSet.Builder<String>().add("the\\s+same\\s+year", "same\\s+year").build();
    private static final String MIDDLE_YEAR_EXPR = ",\\s+|\\s+|\\s+of\\s+|\\s+in\\s+";
    private static final Set<String> CURRENT_MONTH_EXPR = new ImmutableSet.Builder<String>().add("the\\s+this\\s+", "the\\s+current\\s+", "this\\s+", "current\\s+", "running\\s+").build();
    private static final Set<String> PREVIOUS_MONTH_EXPR = new ImmutableSet.Builder<String>().add("the\\s+previous\\s+", "previous\\s+").build();
    private static final Set<String> NEXT_MONTH_EXPR = new ImmutableSet.Builder<String>().add("the\\s+upcoming\\s+", "the\\s+coming\\s+", "the\\s+next\\s+", "upcoming\\s+", "coming\\s+", "next\\s+").build();
    private static final Set<String> SAME_MONTH_EXPR = new ImmutableSet.Builder<String>().add("the\\s+same\\s+month", "same\\s+month").build();
    private static final String MONTHS = "January|February|March|April|(?-i:M)ay|June|July|August|September|October|November|December";
    private static final String MONTHS_PREFIX = "the\\s+month\\s+|month\\s+|the\\s+month\\s+of\\s+|month\\s+of\\s+|the\\s+";
    private static final Set<String> CURRENT_WEEK_EXPR = new ImmutableSet.Builder<String>().add("the\\s+this", "the\\s+current", "this", "current").build();
    private static final Set<String> PREVIOUS_WEEK_EXPR = new ImmutableSet.Builder<String>().add("the\\s+previous", "previous").build();
    private static final Set<String> NEXT_WEEK_EXPR = new ImmutableSet.Builder<String>().add("the\\s+upcoming", "the\\s+coming", "the\\s+next", "upcoming", "coming", "next").build();
    private static final Set<String> SAME_WEEK_EXPR = new ImmutableSet.Builder<String>().add("the\\s+same\\s+week", "same\\s+week").build();
    private static final Set<String> FIRST_WEEK_EXPR = new ImmutableSet.Builder<String>().add("the\\s+first\\s+week\\s+", "the\\s+first\\s+week\\s+of\\s+", "the\\s+first\\s+week,\\s+", "first\\s+week\\s+", "first\\s+week\\s+of\\s+", "first\\s+week,\\s+").build();
    private static final Set<String> LAST_WEEK_EXPR = new ImmutableSet.Builder<String>().add("the\\s+last\\s+week\\s+", "the\\s+last\\s+week\\s+of\\s+", "the\\s+last\\s+week,\\s+", "last\\s+week\\s+", "last\\s+week\\s+of\\s+", "last\\s+week,\\s+").build();
    private static final Set<String> START_OF_EXPR = new ImmutableSet.Builder<String>().add("start\\s+of\\s+", "beginning\\s+of\\s+", "first\\s+month\\s+of\\s+").build();
    private static final Set<String> MID_OF_EXPR = new ImmutableSet.Builder<String>().add("mid\\s+of\\s+").build();
    private static final Set<String> LAST_OF_EXPR = new ImmutableSet.Builder<String>().add("end\\s+of\\s+", "last\\s+month\\s+of\\s+").build();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<ParserNode, Pattern> patternMap = new LinkedHashMap<>();
    private boolean isHistory;
    private LocalDate messageDate;

    PromiseDateExtractor() {
        List<ParserNode> parserNodes = new LinkedList<>();
        parserNodes.add(new Alert1Node(""));
        parserNodes.add(new Alert3_13Node(""));
        parserNodes.add(new Alert4Node(""));
        parserNodes.add(new Alert7Node(""));
        parserNodes.add(new Alert8Node(""));
        parserNodes.add(new Alert14Node(""));
        parserNodes.add(new Alert15Node(""));
        parserNodes.add(new YearOfBirthNode(""));
        parserNodes.add(new YearNode(""));//1,2а, 3а, 4а,2b, 3b, 4b
        parserNodes.add(new YearPrefixNode(""));//5-7
        parserNodes.add(new MonthPrefixNode(""));//9-11
        parserNodes.add(new YearPrefixWithMonthNode_12(""));//12
        parserNodes.add(new WeekPrefixNode(""));//13-15
        parserNodes.add(new WeekPrefixWithYearNode(""));//16b,17b
        parserNodes.add(new WeekPrefixWithYearWithMonthNode(""));//16a,17a
        parserNodes.add(new DayMonthYearNode(""));//19
        parserNodes.add(new YearPrefixWithMonthNode_24_26(""));//24-26
        parserNodes.add(new YearSameNode(""));//21
        parserNodes.add(new SameMonthNode(""));//22
        parserNodes.add(new SameWeekNode(""));//23

        for (ParserNode parserNode : parserNodes) {
            System.out.println(parserNode.getRegexp());
            patternMap.put(parserNode, Pattern.compile(parserNode.getRegexp(), Pattern.CASE_INSENSITIVE));
        }
    }

    public List<PromiseDate> extract(String text, LocalDate baseDate, boolean isHistory) {
        messageDate = baseDate;
        this.isHistory = isHistory;
        List<PromiseDate> promiseDates = new LinkedList<>();

        for (ParserNode parserNode : patternMap.keySet()) {
            Matcher m = patternMap.get(parserNode).matcher(text);
            System.out.println(m.pattern().pattern());
            System.out.println();
            System.out.println();
            System.out.println();
            while (m.find()) {
                PromiseDate promiseDate = parserNode.onMatch(m);
                if (promiseDate != null) {
                    promiseDates.add(promiseDate);
                }
            }
        }

        //todo проверка на частичное пересечение периодов
        //todo проверка на пробел и тд. между датами
        //todo исключение полного совпадения

        Set<PromiseDate> setPromise = new LinkedHashSet<>();
        for (int i = 0, promiseDatesSize = promiseDates.size(); i < promiseDatesSize; i++) {
            final PromiseDate promiseDate1 = promiseDates.get(i);

            for (int j = 0, promiseDatesSize2 = promiseDates.size(); j < promiseDatesSize2; j++) {
                final PromiseDate promiseDate2 = promiseDates.get(j);
                if (j != i) {
                    if (!(promiseDate1.getStartIndex() == promiseDate2.getStartIndex() && promiseDate1.getEndIndex() == promiseDate2.getEndIndex())
                            && (promiseDate1.getStartIndex() >= promiseDate2.getStartIndex() && promiseDate1.getStartIndex() <= promiseDate2.getEndIndex()
                            && promiseDate1.getEndIndex() >= promiseDate2.getStartIndex() && promiseDate1.getEndIndex() <= promiseDate2.getEndIndex())) {

                        if (promiseDate1.isResolved() && promiseDate2.isResolved()) {
                            if (((promiseDate1.getDateFrom() == promiseDate1.getDateFrom() || promiseDate1.getDateFrom().isAfter(promiseDate2.getDateFrom()) && promiseDate1.getDateFrom().isBefore(promiseDate2.getDateTo())
                                    && (promiseDate1.getDateTo() == promiseDate2.getDateTo() || promiseDate1.getDateTo().isBefore(promiseDate2.getDateTo()))))) {
                                setPromise.add(promiseDate1);
                            }
                        } else {
                            setPromise.add(promiseDate1);
                        }
                    }
                }
            }
        }

        promiseDates.removeAll(setPromise);
        setPromise.clear();

        Set<String> delimiter = Sets.newHashSet(" ", " of ");

        for (final PromiseDate promiseDate1 : promiseDates) {
            for (final PromiseDate promiseDate2 : promiseDates) {

                for (String s : delimiter) {
                    if ((!isNullOrEmpty(promiseDate1.getPromise()) && !isNullOrEmpty(promiseDate2.getPromise()) && !promiseDate1.equals(promiseDate2)) &&
                            (promiseDate1.getEndIndex() + s.length() == promiseDate2.getStartIndex() && Objects.equals(text.substring(promiseDate1.getEndIndex(), promiseDate2.getStartIndex()), s)) &&
                            (text.length() > promiseDate1.getEndIndex() && Objects.equals(text.substring(promiseDate1.getEndIndex(), promiseDate2.getStartIndex()), s))) {
                        promiseDate1.addAlert(PromiseDate.AlertType.ALERT11);
                        promiseDate2.addAlert(PromiseDate.AlertType.ALERT11);
                    }
                }

                if (promiseDate2.isResolved() && promiseDate1.isResolved()) {
                    if ((!promiseDate1.equals(promiseDate2) && promiseDate1.getStartIndex() == promiseDate2.getStartIndex() && promiseDate1.getEndIndex() == promiseDate2.getEndIndex()) &&
                            (!promiseDate1.getDateTo().equals(promiseDate2.getDateTo()) || !promiseDate1.getDateFrom().equals(promiseDate2.getDateFrom()))) {
                        promiseDate1.addAlert(PromiseDate.AlertType.ALERT10);
                        promiseDate2.addAlert(PromiseDate.AlertType.ALERT10);
                    }
                }
                if (promiseDate1.getStartIndex() < promiseDate2.getStartIndex() && promiseDate2.getStartIndex() < promiseDate1.getEndIndex() &&
                        promiseDate2.getEndIndex() > promiseDate1.getEndIndex()) {
                    promiseDate1.addAlert(PromiseDate.AlertType.ALERT9);
                    promiseDate2.addAlert(PromiseDate.AlertType.ALERT9);
                }
            }
        }

        promiseDates.removeIf(p -> !p.isShow() && p.getAlerts().isEmpty());
        promiseDates.sort(Comparator.comparingInt(PromiseDate::getStartIndex));
        return promiseDates;
    }


    private Month getMonth(String founded) {
        String monthString = null;
        String normalized = founded.toLowerCase().replaceAll("\\s+", "\\\\s+");

        if (START_OF_EXPR.contains(normalized)) {
            monthString = "January";
        } else if (MID_OF_EXPR.contains(normalized)) {
            monthString = "June";
        } else if (LAST_OF_EXPR.contains(normalized)) {
            monthString = "December";
        } else {
            Matcher m = Pattern.compile(MONTHS, Pattern.CASE_INSENSITIVE).matcher(founded);
            monthString = m.find() ? m.group() : null;
        }
        if (monthString != null) {
            return Month.valueOf(monthString.toUpperCase());
        }
        return null;
    }

    private List<PromiseDate> extractBySame(String text, List<ParserNode> parserNodes) {
        final String regexp = Joiner.on("|").join(parserNodes.stream().map(ParserNode::getRegexp).toArray());
        final String pattern = "(^|\\b)" + regexp + "(\\b|$)";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
        List<PromiseDate> promiseDates = new LinkedList<>();

        while (m.find()) {
            for (ParserNode parserNode : parserNodes) {
                PromiseDate promiseDate = parserNode.onMatch(m);
                //получить промис. проверить на резолв, порешать. не опрешался, взять ноду ниже
                if (promiseDate != null) {
                    if (promiseDate.isResolved()) {
                        promiseDates.add(promiseDate);
                    } else {
                        if (!isHistory && promiseDate.getAlerts() != null) {
                            promiseDates.add(promiseDate);
                        }
                    }
                }
            }
        }
        return promiseDates;
    }


    //1-4
    private class YearNode extends ParserNode {
        private final String NAME_NODE = "Year";
        private final String FULL_NAME_NODE;

        YearNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            Set<String> PREFIX = new ImmutableSet.Builder<String>().addAll(ACCURATE_YEAR_EXPR).add("year").build();
            String yearPrefix = Joiner.on("|").join(PREFIX);
            String yearPrefixRegexp = Joiner.on("\\s+|").join(Iterables.concat(PREVIOUS_YEAR_EXPR, CURRENT_YEAR_EXPR, NEXT_YEAR_EXPR)) + "\\s+";
            String year = Joiner.on("|").join(ACCURATE_YEAR_EXPR);//1
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + "((?<" + FULL_NAME_NODE + "ExprYear>(" + "(?<" + FULL_NAME_NODE + "ExprYearPrefix>" + yearPrefixRegexp + "))(" + yearPrefix + ")))|(?<" + FULL_NAME_NODE + "SimpleYear>" + year + ")" + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }
            PromiseDate promiseDate = null;

            String exprYear = m.group(FULL_NAME_NODE + "ExprYear");
            if (exprYear != null) {
                boolean isCorrected_1 = true;

                Temporal temporal = getYear(exprYear);
                if (temporal != null) {

                    exprYear = m.group(FULL_NAME_NODE + "ExprYearPrefix");
                    if (isContains(exprYear, PREVIOUS_YEAR_EXPR)) {
                        if (((Year) temporal).getValue() != messageDate.getYear() - 1) {
                            isCorrected_1 = false;
                        }
                    } else if (isContains(exprYear, CURRENT_YEAR_EXPR)) {
                        if (((Year) temporal).getValue() != messageDate.getYear()) {
                            isCorrected_1 = false;

                        }
                    } else if (isContains(exprYear, NEXT_YEAR_EXPR)) {
                        if (((Year) temporal).getValue() != messageDate.getYear() + 1) {
                            isCorrected_1 = false;
                        }
                    }

                    if (isCorrected_1) {
                        promiseDate = PromiseDate.resolved(promise, temporal, PromiseDate.PromisePrecision.YEAR, m.start(), m.end());
                    } else {
                        if (isHistory) {
                            promiseDate = PromiseDate.uncorrectedAndResolved(promise, temporal, PromiseDate.PromisePrecision.YEAR, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                        } else {
                            promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                        }
                    }
                }

            } else {
                String simpleYear = m.group(FULL_NAME_NODE + "SimpleYear");
                if (simpleYear != null) {
                    Temporal temporal = getYear(simpleYear);
                    if (temporal != null) {
                        promiseDate = PromiseDate.resolved(promise, temporal, PromiseDate.PromisePrecision.YEAR, m.start(), m.end());
                    }
                }
            }
            if (promiseDate == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return promiseDate;
        }

        private boolean isContains(String text, Set<String> patterns) {
            return Pattern.compile(Joiner.on("\\s+|").join(patterns), Pattern.CASE_INSENSITIVE).matcher(text).find();
        }

        private Year getYear(String founded) {
            Year result = null;
            String yearString = null;
            Integer year = null;


            Matcher m = Pattern.compile("\\d{4}").matcher(founded);
            if (m.find()) {
                yearString = m.group();
                year = Integer.valueOf(yearString);
            }

            if (isContains(founded, PREVIOUS_YEAR_EXPR)) {
                if (year != null && year != messageDate.getYear() - 1) {
                    yearString = "" + (year);
                } else {
                    yearString = "" + (messageDate.getYear() - 1);
                }
            } else if (isContains(founded, CURRENT_YEAR_EXPR)) {
                if (year != null) {
                    yearString = "" + year;
                } else {
                    yearString = "" + (messageDate.getYear());
                }
            } else if (isContains(founded, NEXT_YEAR_EXPR)) {
                if (year != null && year != messageDate.getYear() + 1) {
                    yearString = "" + (year);
                } else {
                    yearString = "" + (messageDate.getYear() + 1);
                }
            }

            if (yearString != null) {
                year = Integer.parseInt(yearString);
                if (year >= 2010 && year <= 2113) {
                    result = Year.of(year);
                }
            }
            return result;
        }
    }

    //8
//Вспомогательный, сам не резолвит
    private class MonthNode extends ParserNode {
        private final String NAME_NODE = "Month";
        private final String FULL_NAME_NODE;

        MonthNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            return "(?<" + FULL_NAME_NODE + ">" + "((((" + MONTHS_PREFIX + ")?)(" + MONTHS + "))))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;//it group is not found
            }

            Month month = getMonth(promise);
            if (month != null) {

                Matcher matcher = Pattern.compile(MONTHS, Pattern.CASE_INSENSITIVE).matcher(promise);
                matcher.find();
                String monthString = matcher.group();
                boolean isUpperCase = Character.isUpperCase(monthString.charAt(0));

                //игнорировать год при получении
                Temporal temporal = YearMonth.of(messageDate.getYear(), month);
                if (isUpperCase) {
                    return PromiseDate.resolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                } else {
                    if (isHistory) {
                        return PromiseDate.uncorrectedAndResolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT8));
                    } else {
                        return PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT8));
                    }
                }
            }
            return null;
        }
    }

    //18
//Вспомогательный, сам не резолвит
    private class DayNode extends ParserNode {
        private final String NAME_NODE = "Day";
        private final String FULL_NAME_NODE;

        private final String prefix = "the\\s+";
        private final String days = "1|2|3|4|5|6|7|8|9|10|11|12|13|14|15|16|17|18|19|20|21|22|23|24|25|26|27|28|29|30|31";

        DayNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            return "(?<" + FULL_NAME_NODE + ">" + "(((" + prefix + ")(" + days + "))|(" + days + ")))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            throw new UnsupportedOperationException();
        }
    }

    //21
    private class YearSameNode extends ParserNode {
        private final String NAME_NODE = "YearSame";
        private final String FULL_NAME_NODE;

        YearSameNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            return "(?<" + FULL_NAME_NODE + ">" + "(^|\\b)" + Joiner.on("|").join(SAME_YEAR_EXPR) + "(\\b|$)" + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }
            PromiseDate promiseDate;
            StringBuffer original = new StringBuffer();
            m.appendTail(original);
            List<PromiseDate> promiseDates = extractBySame(original.substring(0, m.start()), Collections.singletonList(new YearNode(FULL_NAME_NODE)));//1-4
            if (!promiseDates.isEmpty()) {
                promiseDate = promiseDates.get(promiseDates.size() - 1);
                if (promiseDate.isCorrect()) {
                    promiseDate = PromiseDate.resolved(promise, promiseDate.getValue(), promiseDate.getPromisePrecision(), promiseDate.getDateFrom(), promiseDate.getDateTo(), m.start(), m.end());
                } else {
                    if (promiseDate.isResolved()) {
                        if (isHistory) {
                            promiseDate = PromiseDate.resolved(promise, promiseDate.getValue(), promiseDate.getPromisePrecision(), promiseDate.getDateFrom(), promiseDate.getDateTo(), m.start(), m.end());
                        } else {
                            promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT4));
                        }
                    } else {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT4));
                    }
                }
            } else {
                promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT4));
            }
            if (promiseDate == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return promiseDate;
        }
    }

    //9-11
    private class MonthPrefixNode_9b_11b extends ParserNode {
        private final MonthNode monthNode;//8
        private final String NAME_NODE = "MonthPrefix9b11b";
        private final String FULL_NAME_NODE;

        private final Function<LocalDate, YearMonth> getValidDate = dateTime -> {
            if (dateTime != null) {
                int year = dateTime.getYear();
                if (year >= 2010 && year <= 2113) {
                    return YearMonth.from(dateTime);
                }
            }
            return null;
        };

        MonthPrefixNode_9b_11b(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            monthNode = new MonthNode(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            String regexpPrefix = Joiner.on("|").join(Iterables.concat(PREVIOUS_MONTH_EXPR, CURRENT_MONTH_EXPR, NEXT_MONTH_EXPR));
            String monthsPrefix = monthNode.getRegexp();
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + "(" + "?<" + FULL_NAME_NODE + "Exp>(" + regexpPrefix + "))" + "(?<" + FULL_NAME_NODE + "Simple>(?<" + FULL_NAME_NODE + "MPREF>" + monthsPrefix + "))" + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }

            PromiseDate promiseDate = null;
            String prefix = m.group(FULL_NAME_NODE + "Exp");
            YearMonth yearMonth;
            String month = m.group(FULL_NAME_NODE + "Simple");
            boolean corrected = true;

            if (month != null) {
                Month monthParse = getMonth(month);
                yearMonth = getYearMonth(prefix, monthParse);

                promiseDate = monthNode.onMatch(m);//8
                prefix = prefix.toLowerCase().replaceAll("\\s+", "\\\\s+");
                if (PREVIOUS_MONTH_EXPR.contains(prefix)) {
                    if ((!yearMonth.equals(YearMonth.from(messageDate).minusMonths(1)))) {
                        corrected = false;
                    }
                } else if (CURRENT_MONTH_EXPR.contains(prefix)) {
                    if ((!yearMonth.equals(YearMonth.from(messageDate)))) {
                        corrected = false;
                    }
                } else if (NEXT_MONTH_EXPR.contains(prefix)) {
                    if ((!yearMonth.equals(YearMonth.from(messageDate).plusMonths(1)))) {
                        corrected = false;
                    }
                }

                if (!corrected || (promiseDate != null && !promiseDate.isCorrect())) {
                    if (isHistory) {
                        promiseDate = PromiseDate.uncorrectedAndResolved(promise, yearMonth, PromiseDate.PromisePrecision.MONTH, m.start(), m.end(), promiseDate.getAlerts());
                        if (!corrected) {
                            promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                        }
                    } else {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), promiseDate.getAlerts());
                        if (!corrected) {
                            promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                        }
                    }
                } else {
                    promiseDate = PromiseDate.resolved(promise, yearMonth, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                }

            } else {
                yearMonth = getYearMonth(prefix);
                if (yearMonth != null) {
                    promiseDate = monthNode.onMatch(m);//8
                    if (promiseDate.isCorrect()) {
                        promiseDate = PromiseDate.resolved(promise, yearMonth, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                    } else {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), monthNode.onMatch(m).getAlerts());
                        promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                    }
                } else {
                    promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                }
            }
            if (promiseDate == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return promiseDate;
        }


        private YearMonth getYearMonth(String founded, Month month) {
            YearMonth yearMonth = null;
            if (founded.toLowerCase().endsWith("month")) {
                founded = founded.substring(0, founded.length() - "month".length());
            }

            founded = founded.toLowerCase().replaceAll("\\s+", "\\\\s+");
            if (PREVIOUS_MONTH_EXPR.contains(founded)) {
                if (month != null) {
                    if (messageDate.getMonth().getValue() > month.getValue()) {
                        yearMonth = getValidDate.apply(messageDate);
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    } else {
                        yearMonth = getValidDate.apply(messageDate.minusYears(1));
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    }
                }
                yearMonth = getValidDate.apply(messageDate.minusMonths(1));

            } else if (CURRENT_MONTH_EXPR.contains(founded)) {
                yearMonth = getValidDate.apply(messageDate);
                if (month != null) {
                    return YearMonth.of(yearMonth.getYear(), month.getValue());
                }
            } else if (NEXT_MONTH_EXPR.contains(founded)) {
                if (month != null) {
                    if (messageDate.getMonth().getValue() < month.getValue()) {
                        yearMonth = getValidDate.apply(messageDate);
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    } else {
                        yearMonth = getValidDate.apply(messageDate.plusYears(1));
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    }
                }
                yearMonth = getValidDate.apply(messageDate.plusMonths(1));
            } else {
                if (month != null) {
                    return YearMonth.of(messageDate.getYear(), month.getValue());
                }
            }
            return yearMonth;
        }

        private YearMonth getYearMonth(String founded) {
            YearMonth yearMonth = null;
            if (founded.toLowerCase().endsWith("month")) {
                founded = founded.substring(0, founded.length() - "month".length());
            }

            founded = founded.toLowerCase().replaceAll("\\s+", "\\\\s+");
            if (PREVIOUS_MONTH_EXPR.contains(founded)) {
                yearMonth = getValidDate.apply(messageDate.minusMonths(1));
            } else if (CURRENT_MONTH_EXPR.contains(founded)) {
                yearMonth = getValidDate.apply(messageDate);
            } else if (NEXT_MONTH_EXPR.contains(founded)) {
                if (isHistory) {
                    yearMonth = getValidDate.apply(messageDate.plusYears(1));
                } else {
                    yearMonth = getValidDate.apply(messageDate.plusMonths(1));
                }
            } else {
                Month month = getMonth(founded);
                if (month != null) {
                    yearMonth = getValidDate.apply(messageDate).withMonth(month.getValue());
                }
            }
            return yearMonth;
        }
    }

    //5-7
    private class YearPrefixNode extends ParserNode {
        private final YearNode yearNode;
        private final YearSameNode yearSameNode;
        private final String NAME_NODE = "YearPrefix";
        private final String FULL_NAME_NODE;

        YearPrefixNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            yearNode = new YearNode(FULL_NAME_NODE);
            yearSameNode = new YearSameNode(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            String prefixYearExpr = Joiner.on("|").join(Iterables.concat(START_OF_EXPR, MID_OF_EXPR, LAST_OF_EXPR));
            String prefixYearGroupExpr = "(?<" + FULL_NAME_NODE + "PrefixYear" + ">" + "(" + prefixYearExpr + ")" + ")";
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + prefixYearGroupExpr + "(" + yearNode.getRegexp() + "|" + yearSameNode.getRegexp() + ")" + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }

            PromiseDate yearPromiseDate = yearNode.onMatch(m);

            if (yearPromiseDate == null) {
                yearPromiseDate = yearSameNode.onMatch(m);
            }
            if (yearPromiseDate != null) {
                if (!yearPromiseDate.isCorrect()) {
                    if (isHistory) {
                        if (yearPromiseDate.isResolved()) {
                            Month month = getMonth(m.group(FULL_NAME_NODE + "PrefixYear"));
                            if (month != null) {
                                Temporal temporal = ((Year) yearPromiseDate.getValue()).atMonth(month);
                                yearPromiseDate = PromiseDate.uncorrectedAndResolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end(), yearPromiseDate.getAlerts());
                            } else {
                                yearPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                            }
                        } else {
                            yearPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), yearPromiseDate.getAlerts());
                        }
                    } else {
                        yearPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), yearPromiseDate.getAlerts());
                    }
                } else {
                    if (yearPromiseDate.isResolved()) {
                        Month month = getMonth(m.group(FULL_NAME_NODE + "PrefixYear"));
                        if (month != null) {
                            Temporal temporal = ((Year) yearPromiseDate.getValue()).atMonth(month);
                            yearPromiseDate = PromiseDate.resolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                        } else {
                            yearPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                        }
                    }
                }
            } else {
                yearPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
            }
            if (yearPromiseDate == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return yearPromiseDate;
        }
    }

    //9-11
    private class MonthPrefixNode extends ParserNode {
        private final MonthNode monthNode;//8
        private final String NAME_NODE = "MonthPrefix";
        private final String FULL_NAME_NODE;

        private final Function<LocalDate, YearMonth> getValidDate = dateTime -> {
            if (dateTime != null) {
                int year = dateTime.getYear();
                if (year >= 2010 && year <= 2113) {
                    return YearMonth.from(dateTime);
                }
            }
            return null;
        };

        MonthPrefixNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            monthNode = new MonthNode(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            String regexpPrefix = Joiner.on("|").join(Iterables.concat(PREVIOUS_MONTH_EXPR, CURRENT_MONTH_EXPR, NEXT_MONTH_EXPR));
            String monthsPrefix = monthNode.getRegexp();
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + "(" + "?<" + FULL_NAME_NODE + "Exp>(" + regexpPrefix + ")?)" + "(?<" + FULL_NAME_NODE + "Simple>((?<" + FULL_NAME_NODE + "MPREF>" + monthsPrefix + ")|(((" + MONTHS_PREFIX + ")?)(month))" + "))" + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }

            PromiseDate promiseDate = null;
            String prefix = m.group(FULL_NAME_NODE + "Exp");
            if (prefix != null && !prefix.isEmpty()) {
                YearMonth yearMonth;
                String month = m.group(FULL_NAME_NODE + "Simple");
                boolean corrected = true;

                if (month != null) {
                    Month monthParse = getMonth(month);
                    yearMonth = getYearMonth(prefix, monthParse);
                    if (!month.trim().toLowerCase().equals("month")) {

                        promiseDate = monthNode.onMatch(m);//8

                        prefix = prefix.toLowerCase().replaceAll("\\s+", "\\\\s+");
                        if (PREVIOUS_MONTH_EXPR.contains(prefix)) {
                            if ((!yearMonth.equals(YearMonth.from(messageDate).minusMonths(1)))) {
                                corrected = false;
                            }
                        } else if (CURRENT_MONTH_EXPR.contains(prefix)) {
                            if ((!yearMonth.equals(YearMonth.from(messageDate)))) {
                                corrected = false;
                            }
                        } else if (NEXT_MONTH_EXPR.contains(prefix)) {
                            if ((!yearMonth.equals(YearMonth.from(messageDate).plusMonths(1)))) {
                                corrected = false;
                            }
                        }

                        if (!corrected || (promiseDate != null && !promiseDate.isCorrect())) {
                            if (isHistory) {
                                promiseDate = PromiseDate.uncorrectedAndResolved(promise, yearMonth, PromiseDate.PromisePrecision.MONTH, m.start(), m.end(), promiseDate.getAlerts());
                                if (!corrected) {
                                    promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                                }
                            } else {
                                promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), promiseDate.getAlerts());
                                if (!corrected) {
                                    promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                                }
                            }
                        } else {
                            promiseDate = PromiseDate.resolved(promise, yearMonth, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                        }
                    } else {
                        promiseDate = PromiseDate.resolved(promise, yearMonth, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                    }
                } else {
                    yearMonth = getYearMonth(prefix);
                    if (yearMonth != null) {
                        promiseDate = monthNode.onMatch(m);//8
                        if (promiseDate.isCorrect()) {
                            promiseDate = PromiseDate.resolved(promise, yearMonth, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                        } else {
                            promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), monthNode.onMatch(m).getAlerts());
                            promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                        }
                    } else {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                    }
                }
            } else {
                String monthPref = m.group(FULL_NAME_NODE + "MPREF");

                if (monthPref != null && !monthPref.isEmpty()) {
                    promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), monthNode.onMatch(m).getAlerts());
                    promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                }
            }
            if (promiseDate == null) {
                if (!((promise.toLowerCase().endsWith("month")) && isNullOrEmpty(prefix))) {
                    log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
                }
            }
            return promiseDate;
        }


        private YearMonth getYearMonth(String founded, Month month) {
            YearMonth yearMonth = null;
            if (founded.toLowerCase().endsWith("month")) {
                founded = founded.substring(0, founded.length() - "month".length());
            }

            founded = founded.toLowerCase().replaceAll("\\s+", "\\\\s+");
            if (PREVIOUS_MONTH_EXPR.contains(founded)) {
                if (month != null) {
                    if (messageDate.getMonth().getValue() > month.getValue()) {
                        yearMonth = getValidDate.apply(messageDate);
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    } else {
                        yearMonth = getValidDate.apply(messageDate.minusYears(1));
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    }
                }
                yearMonth = getValidDate.apply(messageDate.minusMonths(1));

            } else if (CURRENT_MONTH_EXPR.contains(founded)) {
                yearMonth = getValidDate.apply(messageDate);
                if (month != null) {
                    return YearMonth.of(yearMonth.getYear(), month.getValue());
                }
            } else if (NEXT_MONTH_EXPR.contains(founded)) {
                if (month != null) {
                    if (messageDate.getMonth().getValue() < month.getValue()) {
                        yearMonth = getValidDate.apply(messageDate);
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    } else {
                        yearMonth = getValidDate.apply(messageDate.plusYears(1));
                        return YearMonth.of(yearMonth.getYear(), month.getValue());
                    }
                }
                yearMonth = getValidDate.apply(messageDate.plusMonths(1));
            } else {
                if (month != null) {
                    return YearMonth.of(messageDate.getYear(), month.getValue());
                }
            }
            return yearMonth;
        }

        private YearMonth getYearMonth(String founded) {
            YearMonth yearMonth = null;
            if (founded.endsWith("month")) {
                founded = founded.substring(0, founded.length() - "month".length());
            }

            founded = founded.toLowerCase().replaceAll("\\s+", "\\\\s+");
            if (PREVIOUS_MONTH_EXPR.contains(founded)) {
                yearMonth = getValidDate.apply(messageDate.minusMonths(1));
            } else if (CURRENT_MONTH_EXPR.contains(founded)) {
                yearMonth = getValidDate.apply(messageDate);
            } else if (NEXT_MONTH_EXPR.contains(founded)) {
                if (isHistory) {
                    yearMonth = getValidDate.apply(messageDate.plusYears(1));
                } else {
                    yearMonth = getValidDate.apply(messageDate.plusMonths(1));
                }
            } else {
                Month month = getMonth(founded);
                if (month != null) {
                    yearMonth = getValidDate.apply(messageDate).withMonth(month.getValue());
                }
            }
            return yearMonth;
        }
    }

    //12
    private class YearPrefixWithMonthNode_12 extends ParserNode {
        private final String NAME_NODE = "YearPrefixWithMonth";
        private final String FULL_NAME_NODE;
        private final YearNode yearNode;//1-4
        private final YearSameNode yearSameNode;//21
        private final MonthNode monthNode;//8


        YearPrefixWithMonthNode_12(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            yearNode = new YearNode(FULL_NAME_NODE);
            yearSameNode = new YearSameNode(FULL_NAME_NODE);
            monthNode = new MonthNode(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            String yearRegexp = yearNode.getRegexp();
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + "((" + monthNode.getRegexp() + "))(" + MIDDLE_YEAR_EXPR + ")(" + yearRegexp + "|" + yearSameNode.getRegexp() + ")" + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }
            PromiseDate result = null;
            PromiseDate yearPromiseDate = yearNode.onMatch(m);

            if (yearPromiseDate == null) {
                yearPromiseDate = yearSameNode.onMatch(m);
            }

            if (yearPromiseDate != null) {
                if (!yearPromiseDate.isCorrect()) {
                    PromiseDate monthPromiseDate = monthNode.onMatch(m);
                    if (monthPromiseDate != null && monthPromiseDate.isResolved() && yearPromiseDate.isResolved()) {
                        Temporal temporal = ((Year) yearPromiseDate.getValue()).atMonth(((YearMonth) monthPromiseDate.getValue()).getMonth());
                        if (isHistory) {
                            result = PromiseDate.uncorrectedAndResolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end(), Sets.union(yearPromiseDate.getAlerts(), monthPromiseDate.getAlerts()));
                        } else {
                            result = PromiseDate.alerted(promise, m.start(), m.end(), Sets.union(yearPromiseDate.getAlerts(), monthPromiseDate.getAlerts()));
                        }
                    } else {
                        if (monthPromiseDate != null) {
                            result = PromiseDate.alerted(promise, m.start(), m.end(), Sets.union(yearPromiseDate.getAlerts(), monthPromiseDate.getAlerts()));
                        } else {
                            result = PromiseDate.alerted(promise, m.start(), m.end(), yearPromiseDate.getAlerts());
                        }
                    }
                } else {
                    if (yearPromiseDate.isResolved()) {
                        PromiseDate monthPromiseDate = monthNode.onMatch(m);
                        if (monthPromiseDate != null) {
                            if (monthPromiseDate.isCorrect()) {
                                Temporal temporal = ((Year) yearPromiseDate.getValue()).atMonth(((YearMonth) monthPromiseDate.getValue()).getMonth());
                                result = PromiseDate.resolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                            } else {
                                if (isHistory) {
                                    Temporal temporal = ((Year) yearPromiseDate.getValue()).atMonth(((YearMonth) monthPromiseDate.getValue()).getMonth());
                                    result = PromiseDate.uncorrectedAndResolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end(), monthPromiseDate.getAlerts());
                                } else {
                                    result = PromiseDate.alerted(promise, m.start(), m.end(), Sets.union(monthPromiseDate.getAlerts(), yearPromiseDate.getAlerts()));
                                }
                            }
                        }
                    }
                }
            } else {
                result = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
            }
            if (result == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return result;
        }
    }

    //24-26
    private class YearPrefixWithMonthNode_24_26 extends ParserNode {
        private final String NAME_NODE = "YearPrefixWithMonth2426";
        private final String FULL_NAME_NODE;
        private final YearNode yearNode;//1-4
        private final YearSameNode yearSameNode;//21
        private final MonthPrefixNode_9b_11b monthPrefixNode;//9-11


        YearPrefixWithMonthNode_24_26(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            yearNode = new YearNode(FULL_NAME_NODE);
            yearSameNode = new YearSameNode(FULL_NAME_NODE);
            monthPrefixNode = new MonthPrefixNode_9b_11b(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            String yearRegexp = yearNode.getRegexp();
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + "((" + monthPrefixNode.getRegexp() + "))(" + MIDDLE_YEAR_EXPR + ")(" + yearRegexp + "|" + yearSameNode.getRegexp() + ")" + "(\\b|$))";
        }

        boolean isContains(String s, Set<String> set) {
            s = s.toLowerCase().replaceAll("\\s+", "\\\\s+");
            for (String s1 : set) {
                if (s.contains(s1)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }
            PromiseDate result = null;

            PromiseDate yearPromiseDate = yearNode.onMatch(m);
            if (yearPromiseDate == null) {
                yearPromiseDate = yearSameNode.onMatch(m);
            }
            if (!isHistory) {
                PromiseDate monthPromiseDate = monthPrefixNode.onMatch(m);
                if (yearPromiseDate != null || monthPromiseDate != null) {
                    if (yearPromiseDate != null && monthPromiseDate != null) {
                        boolean isCorrect1 = true;
                        if (yearPromiseDate.isResolved() && monthPromiseDate.isResolved()) {
                            Temporal temporal = ((Year) yearPromiseDate.getValue()).atMonth(((YearMonth) monthPromiseDate.getValue()).getMonth());
                            if (isContains(monthPromiseDate.getPromise(), PREVIOUS_MONTH_EXPR)) {
                                if ((!temporal.equals(YearMonth.from(messageDate).minusMonths(1)))) {
                                    isCorrect1 = false;
                                }
                            } else if (isContains(monthPromiseDate.getPromise(), CURRENT_MONTH_EXPR)) {
                                if ((!temporal.equals(YearMonth.from(messageDate)))) {
                                    isCorrect1 = false;
                                }
                            } else if (isContains(monthPromiseDate.getPromise(), NEXT_MONTH_EXPR)) {
                                if ((!temporal.equals(YearMonth.from(messageDate).plusMonths(1)))) {
                                    isCorrect1 = false;
                                }
                            }
                            if (yearPromiseDate.isCorrect() && yearPromiseDate.isCorrect() && isCorrect1) {
                                result = PromiseDate.resolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end());
                            } else {
                                result = PromiseDate.alerted(promise, m.start(), m.end(), Sets.union(yearPromiseDate.getAlerts(), monthPromiseDate.getAlerts()));
                                if (!isCorrect1) {
                                    result.addAlert(PromiseDate.AlertType.ALERT1);
                                }
                            }
                        } else {
                            result = PromiseDate.alerted(promise, m.start(), m.end(), Sets.union(yearPromiseDate.getAlerts(), monthPromiseDate.getAlerts()));
                        }
                    } else if (yearPromiseDate != null) {
                        result = PromiseDate.alerted(promise, m.start(), m.end(), yearPromiseDate.getAlerts());
                    } else {
                        result = PromiseDate.alerted(promise, m.start(), m.end(), monthPromiseDate.getAlerts());
                    }
                } else {
                    result = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                }
            } else {
                if (yearPromiseDate != null) {
                    if (yearPromiseDate.isResolved()) {
                        PromiseDate monthPromiseDate = monthPrefixNode.onMatch(m);
                        if (monthPromiseDate != null && monthPromiseDate.isResolved()) {
                            Temporal temporal = ((Year) yearPromiseDate.getValue()).atMonth(((YearMonth) monthPromiseDate.getValue()).getMonth());
                            result = PromiseDate.uncorrectedAndResolved(promise, temporal, PromiseDate.PromisePrecision.MONTH, m.start(), m.end(), Sets.union(yearPromiseDate.getAlerts(), monthPromiseDate.getAlerts()));
                            result.addAlert(PromiseDate.AlertType.ALERT1);
                        } else {
                            result = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                        }
                    } else {
                        result = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                    }
                } else {
                    PromiseDate promiseDate = monthPrefixNode.onMatch(m);
                    if (promiseDate != null) {
                        if (promiseDate.isCorrect()) {
                            result = promiseDate;
                        } else {
                            result = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                        }
                    }
                }
            }
            if (result == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return result;
        }
    }

    //22
    private class SameMonthNode extends ParserNode {
        private final String NAME_NODE = "SameMonth";
        private final String FULL_NAME_NODE;

        SameMonthNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            return "(?<" + FULL_NAME_NODE + ">" + "(^|\\b)" + Joiner.on("|").join(SAME_MONTH_EXPR) + "(\\b|$)" + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }
            PromiseDate promiseDate;

            StringBuffer original = new StringBuffer();
            m.appendTail(original);
            List<PromiseDate> promiseDates = extractBySame(original.substring(0, m.start()),
                    Lists.newArrayList(new YearPrefixWithMonthNode_12(FULL_NAME_NODE), new MonthPrefixNode(FULL_NAME_NODE)));//9-11, 12
            if (!promiseDates.isEmpty()) {
                promiseDate = promiseDates.get(promiseDates.size() - 1);
                if (promiseDate.isCorrect()) {
                    promiseDate = PromiseDate.resolved(promise, promiseDate.getValue(), promiseDate.getPromisePrecision(), promiseDate.getDateFrom(), promiseDate.getDateTo(), m.start(), m.end());
                } else {
                    if (promiseDate.isResolved()) {
                        if (isHistory) {
                            promiseDate = PromiseDate.resolved(promise, promiseDate.getValue(), promiseDate.getPromisePrecision(), promiseDate.getDateFrom(), promiseDate.getDateTo(), m.start(), m.end());
                        } else {
                            promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT5));
                        }
                    } else {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT5));
                    }
                }
            } else {
                promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT5));
            }
            if (promiseDate == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return promiseDate;
        }
    }

    //19
    private class DayMonthYearNode extends ParserNode {
        private final String NAME_NODE = "DayMonthYear";
        private final String FULL_NAME_NODE;
        private final DayNode dayNode;//18
        private final SameMonthNode sameMonthNode;//22
        private final YearPrefixWithMonthNode_12 yearPrefixWithMonthNode;//12
        private final YearPrefixWithMonthNode_24_26 yearPrefixWithMonthNode_24_26;//12
        private final MonthPrefixNode monthPrefixNode;//9-11
        private final YearPrefixNode yearPrefixNode;//запрещен !5-7
        private String middlefix = "\\s+|th\\s+|\\s+of\\s+|,\\s+";

        DayMonthYearNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            yearPrefixWithMonthNode = new YearPrefixWithMonthNode_12(FULL_NAME_NODE);
            yearPrefixWithMonthNode_24_26 = new YearPrefixWithMonthNode_24_26(FULL_NAME_NODE);
            dayNode = new DayNode(FULL_NAME_NODE);
            monthPrefixNode = new MonthPrefixNode(FULL_NAME_NODE);
            sameMonthNode = new SameMonthNode(FULL_NAME_NODE);
            yearPrefixNode = new YearPrefixNode(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + "(?<" + FULL_NAME_NODE + "Simple>" + dayNode.getRegexp() + ")(" + middlefix + ")(((" + yearPrefixWithMonthNode.getRegexp() + ")|(" + yearPrefixWithMonthNode_24_26.getRegexp() + ")|(" + yearPrefixNode.getRegexp() + ")|(" + monthPrefixNode.getRegexp() + ")|(" + sameMonthNode.getRegexp() + ")))" + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }

            String simpleDay = m.group(FULL_NAME_NODE + "Simple");
            if (simpleDay == null) {
                return null;//it group is not found
            }
            Integer day = getDay(simpleDay);
            if (day == null) {
                return null;
            }

            PromiseDate promiseDate = yearPrefixWithMonthNode.onMatch(m);
            if (promiseDate == null) {
                promiseDate = yearPrefixWithMonthNode_24_26.onMatch(m);
            }
            if (promiseDate == null || (!promiseDate.isResolved() && promiseDate.getAlerts() == null)) {
                promiseDate = monthPrefixNode.onMatch(m);
                if (promiseDate == null || !promiseDate.isResolved()) {
                    if (promiseDate == null || promiseDate.isCorrect()) {
                        if (promiseDate == null || promiseDate.isCorrect()) {
                            promiseDate = sameMonthNode.onMatch(m);
                            if (promiseDate == null || !promiseDate.isResolved()) {
                                if (promiseDate == null || promiseDate.isCorrect()) {
                                    promiseDate = monthPrefixNode.onMatch(m);
                                } else {
                                    if (promiseDate != null) {
                                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), promiseDate.getAlerts());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (promiseDate != null) {
                if (!promiseDate.isCorrect()) {
                    if (isHistory) {
                        try {
                            if (promiseDate.getValue() != null) {
                                LocalDate temporal = LocalDate.of(((YearMonth) promiseDate.getValue()).getYear(), ((YearMonth) promiseDate.getValue()).getMonth(), day);
                                promiseDate = PromiseDate.uncorrectedAndResolved(promise, temporal, PromiseDate.PromisePrecision.DAY, m.start(), m.end(), promiseDate.getAlerts());
                            } else {
                                return PromiseDate.alerted(promise, m.start(), m.end(), promiseDate.getAlerts());
                            }
                        } catch (DateTimeException ex) {
                            promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), promiseDate.getAlerts());
                            promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                        }
                    } else {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), promiseDate.getAlerts());
                    }
                } else {
                    if (promiseDate.isResolved()) {
                        try {
                            LocalDate temporal = LocalDate.of(((YearMonth) promiseDate.getValue()).getYear(), ((YearMonth) promiseDate.getValue()).getMonth(), day);
                            promiseDate = PromiseDate.resolved(promise, temporal, PromiseDate.PromisePrecision.DAY, m.start(), m.end());
                        } catch (DateTimeException ex) {
                            promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), promiseDate.getAlerts());
                            promiseDate.addAlert(PromiseDate.AlertType.ALERT1);
                        }
                    }
                }
            } else {
                promiseDate = yearPrefixNode.onMatch(m);
                if (promiseDate != null && promiseDate.isResolved()) {
                    if (!isHistory) {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                    }
                }
            }
            if (promiseDate == null) {
                if (!promise.toLowerCase().endsWith("month")) {
                    log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
                }
            }
            return promiseDate;
        }

        Integer getDay(String founded) {
            Integer day;
            founded = founded.replaceAll("\\D", "");
            try {
                day = Integer.valueOf(founded);
            } catch (NumberFormatException ex) {
                day = null;
            }
            return day;
        }
    }

    //16b,17b
    private class WeekPrefixWithYearNode extends ParserNode {
        private final String NAME_NODE = "WeekPrefixWithYear";
        private final String FULL_NAME_NODE;
        private final YearNode yearNode;//1-4
        private final YearSameNode yearSameNode;//21

        WeekPrefixWithYearNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            yearNode = new YearNode(FULL_NAME_NODE);
            yearSameNode = new YearSameNode(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            String regexpPref = "?<" + FULL_NAME_NODE + "Prefix>" + Joiner.on("|").join(Iterables.concat(LAST_WEEK_EXPR, FIRST_WEEK_EXPR));
            return "(?<" + FULL_NAME_NODE + ">" + "(^|\\b)(" + regexpPref + ")(" + yearNode.getRegexp() + "|" + yearSameNode.getRegexp() + ")(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;//it group is not found
            }

            PromiseDate yearPromiseDate = yearNode.onMatch(m);
            if (yearPromiseDate == null) {
                yearPromiseDate = yearSameNode.onMatch(m);
            }

            if (yearPromiseDate != null) {
                if (yearPromiseDate.isCorrect()) {
                    String week = m.group(FULL_NAME_NODE + "Prefix");
                    if (week != null) {
                        LocalDate localDate = get(week, yearPromiseDate);
                        if (localDate != null) {
                            LocalDate dateTo = localDate.plusDays(6);
                            yearPromiseDate = PromiseDate.resolved(promise, localDate, PromiseDate.PromisePrecision.WEEK, localDate, dateTo, m.start(), m.end());
                        }
                    }
                } else {
                    if (isHistory) {
                        String week = m.group(FULL_NAME_NODE + "Prefix");
                        if (week != null && yearPromiseDate.isResolved()) {
                            LocalDate localDate = get(week, yearPromiseDate);
                            if (localDate != null) {
                                LocalDate dateTo = localDate.plusDays(6);
                                yearPromiseDate = PromiseDate.uncorrectedAndResolved(promise, localDate, PromiseDate.PromisePrecision.WEEK, localDate, dateTo, m.start(), m.end(), yearPromiseDate.getAlerts());
                            }
                        } else {
                            yearPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), yearPromiseDate.getAlerts());
                        }
                    } else {
                        yearPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), yearPromiseDate.getAlerts());
                    }
                }
            }
            if (yearPromiseDate == null) {
                if (!promise.toLowerCase().endsWith("month")) {
                    log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
                }
            }
            return yearPromiseDate;
        }

        private LocalDate get(String founded, PromiseDate promiseDate) {
            LocalDate localDate = null;
            founded = founded.substring(0, founded.length()).toLowerCase().replaceAll("\\s+", "\\\\s+");
            if (LAST_WEEK_EXPR.contains(founded)) {
                localDate = promiseDate.getDateTo().with(lastInMonth(DayOfWeek.MONDAY));
            } else if (FIRST_WEEK_EXPR.contains(founded)) {
                localDate = promiseDate.getDateFrom().with(firstInMonth(DayOfWeek.MONDAY));
            }
            return localDate;
        }
    }

    //16a,17a
    private class WeekPrefixWithYearWithMonthNode extends ParserNode {
        private final String NAME_NODE = "WeekPrefixWithYearWithMonth";
        private final String FULL_NAME_NODE;
        private final SameMonthNode sameMonthNode;//22
        private final YearPrefixWithMonthNode_12 yearPrefixWithMonthNode_12;//12
        private final YearPrefixWithMonthNode_24_26 yearPrefixWithMonthNode_24_26;//24-26
        private final MonthPrefixNode monthPrefixNode;//9-11
        private final MonthNode monthNode;//8
        private final YearPrefixNode yearPrefixNode;//запрещен !5-7

        WeekPrefixWithYearWithMonthNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
            yearPrefixWithMonthNode_12 = new YearPrefixWithMonthNode_12(FULL_NAME_NODE);
            yearPrefixWithMonthNode_24_26 = new YearPrefixWithMonthNode_24_26(FULL_NAME_NODE);
            monthNode = new MonthNode(FULL_NAME_NODE);
            monthPrefixNode = new MonthPrefixNode(FULL_NAME_NODE);
            sameMonthNode = new SameMonthNode(FULL_NAME_NODE);
            yearPrefixNode = new YearPrefixNode(FULL_NAME_NODE);
        }

        @Override
        String getRegexp() {
            String regexpPref = "?<" + FULL_NAME_NODE + "Prefix>" + Joiner.on("|").join(Iterables.concat(LAST_WEEK_EXPR, FIRST_WEEK_EXPR));
            return "(?<" + FULL_NAME_NODE + ">" + "(^|\\b)" + "(" + regexpPref + ")" +
                    "((" + yearPrefixWithMonthNode_12.getRegexp() + ")|(" + yearPrefixWithMonthNode_24_26.getRegexp() + ")|(" + monthPrefixNode.getRegexp() + ")|(" + yearPrefixNode.getRegexp() + ")|(" + sameMonthNode.getRegexp() + ")|(?<" + FULL_NAME_NODE + "AlertMonth56>" + monthNode.getRegexp() + "))" + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            PromiseDate yearMonthPromiseDate = null;
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;//it group is not found
            }
            String week = m.group(FULL_NAME_NODE + "Prefix");
            if (week != null) {
                yearMonthPromiseDate = sameMonthNode.onMatch(m);
                if (yearMonthPromiseDate == null || !yearMonthPromiseDate.isResolved()) {
                    if (yearMonthPromiseDate == null || yearMonthPromiseDate.isCorrect()) {
                        yearMonthPromiseDate = yearPrefixWithMonthNode_12.onMatch(m);
                        if (yearMonthPromiseDate == null || !yearMonthPromiseDate.isResolved()) {
                            if (yearMonthPromiseDate == null || yearMonthPromiseDate.isCorrect()) {
                                yearMonthPromiseDate = monthPrefixNode.onMatch(m);
                            } else {
                                yearMonthPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), yearMonthPromiseDate.getAlerts());
                            }
                        }
                    }
                }
                if (yearMonthPromiseDate == null) {
                    yearMonthPromiseDate = yearPrefixWithMonthNode_24_26.onMatch(m);
                }

                if (yearMonthPromiseDate != null) {
                    if (yearMonthPromiseDate.isCorrect()) {
                        LocalDate localDate = get(week, yearMonthPromiseDate);
                        if (localDate != null) {
                            LocalDate dateTo = localDate.plusDays(6);
                            yearMonthPromiseDate = PromiseDate.resolved(promise, localDate, PromiseDate.PromisePrecision.WEEK, localDate, dateTo, m.start(), m.end());
                        }
                    } else {
                        if (isHistory) {
                            if (yearMonthPromiseDate.isResolved()) {
                                LocalDate localDate = get(week, yearMonthPromiseDate);
                                if (localDate != null) {
                                    LocalDate dateTo = localDate.plusDays(6);
                                    yearMonthPromiseDate = PromiseDate.uncorrectedAndResolved(promise, localDate, PromiseDate.PromisePrecision.WEEK, localDate, dateTo, m.start(), m.end(), yearMonthPromiseDate.getAlerts());
                                }
                            } else {
                                yearMonthPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), yearMonthPromiseDate.getAlerts());
                            }
                        } else {
                            yearMonthPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), yearMonthPromiseDate.getAlerts());
                        }
                    }
                } else {
                    yearMonthPromiseDate = yearPrefixNode.onMatch(m);
                    if (yearMonthPromiseDate != null && yearMonthPromiseDate.isResolved()) {
                        if (!isHistory) {
                            yearMonthPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                        }
                    }
                }

                String group = m.group(FULL_NAME_NODE + "AlertMonth56");
                if (group != null) {
                    yearMonthPromiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                }
            }
            if (yearMonthPromiseDate == null) {
                if (!promise.toLowerCase().endsWith("month")) {
                    log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
                }
            }
            return yearMonthPromiseDate;
        }

        private LocalDate get(String founded, PromiseDate promiseDate) {
            LocalDate localDate = null;
            founded = founded.substring(0, founded.length()).toLowerCase().replaceAll("\\s+", "\\\\s+");
            if (LAST_WEEK_EXPR.contains(founded)) {
                localDate = promiseDate.getDateTo().with(lastInMonth(DayOfWeek.MONDAY));
            } else if (FIRST_WEEK_EXPR.contains(founded)) {
                localDate = promiseDate.getDateFrom().with(firstInMonth(DayOfWeek.MONDAY));
            }
            return localDate;
        }
    }

    //13-15
    private class WeekPrefixNode extends ParserNode {
        private final String NAME_NODE = "WeekPrefix";
        private final String FULL_NAME_NODE;

        WeekPrefixNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String regexp = Joiner.on("\\s+|").join(Iterables.concat(PREVIOUS_WEEK_EXPR, CURRENT_WEEK_EXPR, NEXT_WEEK_EXPR)).concat("\\s+");
            return "(?<" + FULL_NAME_NODE + ">" + "(^|\\b)(" + regexp + ")(week)(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }
            PromiseDate promiseDate = null;
            LocalDate localDate = get(promise);
            if (localDate != null) {
                LocalDate from = localDate.with(DayOfWeek.MONDAY);
                LocalDate to = localDate.with(DayOfWeek.SUNDAY);
                promiseDate = PromiseDate.resolved(promise, localDate, PromiseDate.PromisePrecision.WEEK, from, to, m.start(), m.end());
            }
            if (promiseDate == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return promiseDate;
        }


        private LocalDate get(String founded) {
            LocalDate localDate = null;
            founded = founded.substring(0, founded.length() - " week".length()).toLowerCase().replaceAll("\\s+", "\\\\s+");
            if (NEXT_WEEK_EXPR.contains(founded)) {
                localDate = messageDate.plusWeeks(1);
            } else if (CURRENT_WEEK_EXPR.contains(founded)) {
                localDate = messageDate;
            } else if (PREVIOUS_WEEK_EXPR.contains(founded)) {
                localDate = messageDate.minusWeeks(1);
            }
            return localDate;
        }
    }

    //23
    private class SameWeekNode extends ParserNode {
        private final String NAME_NODE = "SameWeek";
        private final String FULL_NAME_NODE;

        SameWeekNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            return "(?<" + FULL_NAME_NODE + ">(^|\\b)" + Joiner.on("|").join(SAME_WEEK_EXPR) + "(\\b|$))";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }
            StringBuffer original = new StringBuffer();
            m.appendTail(original);
            PromiseDate promiseDate;
            List<PromiseDate> promiseDates = extractBySame(original.substring(0, m.start()),
                    Lists.newArrayList(new WeekPrefixWithYearWithMonthNode(FULL_NAME_NODE), new WeekPrefixNode(FULL_NAME_NODE), new WeekPrefixWithYearNode(FULL_NAME_NODE)));
            if (!promiseDates.isEmpty()) {
                promiseDate = promiseDates.get(promiseDates.size() - 1);
                if (promiseDate.isCorrect()) {
                    promiseDate = PromiseDate.resolved(promise, promiseDate.getValue(), promiseDate.getPromisePrecision(), promiseDate.getDateFrom(), promiseDate.getDateTo(), m.start(), m.end());
                } else {
                    if (promiseDate.isResolved()) {
                        if (isHistory) {
                            promiseDate = PromiseDate.resolved(promise, promiseDate.getValue(), promiseDate.getPromisePrecision(), promiseDate.getDateFrom(), promiseDate.getDateTo(), m.start(), m.end());
                        } else {
                            promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT6));
                        }
                    } else {
                        promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT6));
                    }
                }
            } else {
                promiseDate = PromiseDate.alerted(promise, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT6));
            }
            if (promiseDate == null) {
                log.warn("Promise:{} is match, but not resolved!", promise);//todo избавиться от null
            }
            return promiseDate;
        }
    }

    private class Alert1Node extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "AlertTemplate1";
        private final String FULL_NAME_NODE;

        Alert1Node(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String ALERT_TEMPLATE = "\\/";
            return "(?<" + FULL_NAME_NODE + ">" + ALERT_TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            if (!isHistory) {
                String alert = m.group(FULL_NAME_NODE);
                if (alert == null) {
                    return null;//it group is not found
                }
                String group = m.group(NAME_NODE);
                if (group != null) {
                    return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT2));
                }
            }
            return null;
        }
    }

    private class Alert3_13Node extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "AlertTemplate3";
        private final String FULL_NAME_NODE;

        Alert3_13Node(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String ALERT_TEMPLATE = "(^|\\b)((new\\s+year)|(year))(\\b|$)";
            return "(?<" + FULL_NAME_NODE + ">" + ALERT_TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            if (!isHistory) {
                String alert = m.group(FULL_NAME_NODE);
                if (alert == null) {
                    return null;//it group is not found
                }
                String group = m.group(NAME_NODE);
                if (group != null) {
                    if (group.equals("New Year")) {
                        return null;
                    } else {
                        if (group.toLowerCase().equals("year")) {
                            return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT13));
                        } else {
                            return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT3));
                        }
                    }
                }
            }
            return null;
        }
    }

    private class Alert4Node extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "AlertTemplate4";
        private final String FULL_NAME_NODE;

        Alert4Node(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String ALERT_TEMPLATE = "(^|\\b)(mon|tue|wed|thu|fri|sat)(\\b|$)";
            return "(?<" + FULL_NAME_NODE + ">" + ALERT_TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            if (!isHistory) {
                String alert = m.group(FULL_NAME_NODE);
                if (alert == null) {
                    return null;//it group is not found
                }
                String group = m.group(NAME_NODE);
                if (group != null) {
                    return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT7));
                }
            }
            return null;
        }
    }

    private class Alert7Node extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "AlertTemplate7";
        private final String FULL_NAME_NODE;

        Alert7Node(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String ALERT_TEMPLATE = "(^|\\b)new\\s+week(^|\\b)";
            return "(?<" + FULL_NAME_NODE + ">" + ALERT_TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            if (!isHistory) {
                String alert = m.group(FULL_NAME_NODE);
                if (alert == null) {
                    return null;//it group is not found
                }
                String group = m.group(NAME_NODE);
                if (group != null) {
                    return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                }
            }
            return null;
        }
    }

    private class Alert8Node extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "AlertTemplate8";
        private final String FULL_NAME_NODE;

        Alert8Node(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String ALERT_TEMPLATE = "(^|\\b)new\\s+month(\\b|$)";
            return "(?<" + FULL_NAME_NODE + ">" + ALERT_TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            if (!isHistory) {
                String alert = m.group(FULL_NAME_NODE);
                if (alert == null) {
                    return null;//it group is not found
                }
                String group = m.group(NAME_NODE);
                if (group != null) {
                    return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT1));
                }
            }
            return null;
        }
    }

    private class Alert14Node extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "AlertTemplate14";
        private final String FULL_NAME_NODE;

        Alert14Node(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String ALERT_TEMPLATE = "(^|\\b)(" + DIGITAL_YEAR_REGEXP + "-(d{5,}|\\d{1,3})|(d{5,}|\\d{1,3})-" + DIGITAL_YEAR_REGEXP + ")(\\b|$)";
            return "(?<" + FULL_NAME_NODE + ">" + ALERT_TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            if (!isHistory) {
                String alert = m.group(FULL_NAME_NODE);
                if (alert == null) {
                    return null;//it group is not found
                }
                String group = m.group(NAME_NODE);
                if (group != null) {
                    return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT14));
                }
            }
            return null;
        }
    }

    private class Alert15Node extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "AlertTemplate15";
        private final String FULL_NAME_NODE;

        Alert15Node(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String ALERT_TEMPLATE = "(^|\\b)(last\\s+month)(\\b|$)";
            return "(?<" + FULL_NAME_NODE + ">" + ALERT_TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            if (!isHistory) {
                String alert = m.group(FULL_NAME_NODE);
                if (alert == null) {
                    return null;//it group is not found
                }
                String group = m.group(NAME_NODE);
                if (group != null) {
                    return PromiseDate.alerted(alert, m.start(), m.end(), Collections.singleton(PromiseDate.AlertType.ALERT15));

                }
            }
            return null;
        }
    }


    private class YearOfBirthNode extends ParserNode {//запрещенные шаблоны
        private final String NAME_NODE = "YearOfBirthNode";
        private final String FULL_NAME_NODE;

        YearOfBirthNode(String groupPrefix) {
            FULL_NAME_NODE = groupPrefix + NAME_NODE;
        }

        @Override
        String getRegexp() {
            String TEMPLATE = "(^|\\b)(year\\s+of\\s+birth)(\\b|$)";
            return "(?<" + FULL_NAME_NODE + ">" + TEMPLATE + ")";
        }

        @Override
        PromiseDate onMatch(Matcher m) {
            String promise = m.group(FULL_NAME_NODE);
            if (promise == null) {
                return null;
            }

            return PromiseDate.noShow(promise, m.start(), m.end());
        }
    }

    abstract class ParserNode {
        abstract String getRegexp();

        abstract PromiseDate onMatch(Matcher m);
    }
}
