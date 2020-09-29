package ru.drudenko.parsedate.dto;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class PromiseDate {
    private final String promise;
    private final int startIndex;
    private final int endIndex;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final Temporal value;
    private final boolean resolved;
    private final PromisePrecision promisePrecision;
    private final Set<AlertType> alerts = new LinkedHashSet<>();
    private boolean correct;
    private boolean isShow = true;


    private PromiseDate(String promise,
                        Temporal value,
                        PromisePrecision promisePrecision,
                        LocalDate dateFrom,
                        LocalDate dateTo,
                        int startIndex,
                        int endIndex,
                        boolean resolved,
                        boolean correct,
                        Set<AlertType> alerts,
                        boolean isShow) {
        if (resolved) {
            requireNonNull(value);
            requireNonNull(dateFrom);
            requireNonNull(dateTo);
        }
        this.promise = promise;
        this.value = value;
        this.resolved = resolved;
        this.promisePrecision = promisePrecision;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        checkArgument(startIndex >= 0);
        this.startIndex = startIndex;
        checkArgument(endIndex >= 0);
        this.endIndex = endIndex;
        if (alerts != null) {
            this.alerts.addAll(alerts);
        }
        this.correct = correct;
        this.isShow = isShow;
    }

    public static PromiseDate resolved(String promise, Temporal value, PromisePrecision promisePrecision, int startIndex, int endIndex) {
        LocalDate dateFrom = extractFromDate(value, promisePrecision);
        LocalDate dateTo = extractEndDate(value, promisePrecision);
        return new PromiseDate(promise, value, promisePrecision, dateFrom, dateTo, startIndex, endIndex, true, true, null, true);
    }

    public static PromiseDate resolved(String promise, Temporal value, PromisePrecision promisePrecision, LocalDate dateFrom, LocalDate dateTo, int startIndex, int endIndex) {
        return new PromiseDate(promise, value, promisePrecision, dateFrom, dateTo, startIndex, endIndex, true, true, null, true);
    }

    public static PromiseDate uncorrectedAndResolved(String promise, Temporal value, PromisePrecision promisePrecision, int startIndex, int endIndex, Set<AlertType> alerts) {
        LocalDate dateFrom = extractFromDate(value, promisePrecision);
        LocalDate dateTo = extractEndDate(value, promisePrecision);
        return new PromiseDate(promise, value, promisePrecision, dateFrom, dateTo, startIndex, endIndex, true, false, alerts, true);
    }

    public static PromiseDate uncorrectedAndResolved(String promise, Temporal value, PromisePrecision promisePrecision, LocalDate dateFrom, LocalDate dateTo, int startIndex, int endIndex, Set<AlertType> alerts) {
        return new PromiseDate(promise, value, promisePrecision, dateFrom, dateTo, startIndex, endIndex, true, false, alerts, true);
    }

    public static PromiseDate alerted(String promise, int startIndex, int endIndex, Set<AlertType> alerts) {
        return new PromiseDate(promise, null, null, null, null, startIndex, endIndex, false, false, alerts, true);
    }

    public static PromiseDate noShow(String promise, int startIndex, int endIndex) {
        return new PromiseDate(promise, null, null, null, null, startIndex, endIndex, false, false, null, false);

    }

    private static LocalDate extractEndDate(TemporalAccessor value, PromisePrecision promisePrecision) {
        if (promisePrecision == PromisePrecision.YEAR) {
            return LocalDate.from(((Year) value).atMonth(12).atDay(31));
        } else if (promisePrecision == PromisePrecision.MONTH) {
            return LocalDate.from(((YearMonth) value).atEndOfMonth());
        } else if (promisePrecision == PromisePrecision.DAY) {
            return LocalDate.from(value);
        }
        return null;
    }

    private static LocalDate extractFromDate(TemporalAccessor value, PromisePrecision promisePrecision) {
        if (promisePrecision == PromisePrecision.YEAR) {
            return LocalDate.from(((Year) value).atMonth(1).atDay(1));
        } else if (promisePrecision == PromisePrecision.MONTH) {
            return LocalDate.from(((YearMonth) value).atDay(1));
        } else if (promisePrecision == PromisePrecision.DAY) {
            return LocalDate.from(value);
        }
        return null;
    }

    void resolveFrom(List<PromiseDate> resolved, int thisIndex) {
        resolved.listIterator(thisIndex);
        ListIterator<PromiseDate> dateIterator = resolved.listIterator(resolved.indexOf(this));

        while (dateIterator.hasPrevious()) {
            PromiseDate pd = dateIterator.previous();

            if (pd.isResolved()) {
                //по типу даты
            }
        }
    }

    public void addAlert(AlertType alertType) {
        correct = false;
        alerts.add(alertType);
    }

    public boolean isCorrect() {
        return correct || alerts.isEmpty();
    }

    public String getPromise() {
        return promise;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public Temporal getValue() {
        return value;
    }

    public boolean isResolved() {
        return resolved;
    }

    public PromisePrecision getPromisePrecision() {
        return promisePrecision;
    }

    public Set<AlertType> getAlerts() {
        return alerts;
    }

    public String alertsToString() {
        return alerts.isEmpty() ? null : Joiner.on(",").join(alerts.stream().map(AlertType::getValue).toArray());
    }

    public boolean isShow() {
        return isShow;
    }

    @Override
    public String toString() {
        return "PromiseDate{" +
                "promise='" + promise + '\'' +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                ", dateFrom=" + dateFrom +
                ", dateTo=" + dateTo +
                ", value=" + value +
                ", resolved=" + resolved +
                ", promisePrecision=" + promisePrecision +
                ", correct=" + correct +
                ", alerts='" + alerts + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PromiseDate that = (PromiseDate) o;
        return startIndex == that.startIndex &&
                endIndex == that.endIndex &&
                resolved == that.resolved &&
                correct == that.correct &&
                Objects.equal(promise, that.promise) &&
                Objects.equal(dateFrom, that.dateFrom) &&
                Objects.equal(dateTo, that.dateTo) &&
                Objects.equal(value, that.value) &&
                promisePrecision == that.promisePrecision &&
                alerts == that.alerts;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(promise, startIndex, endIndex, dateFrom, dateTo, value, resolved, promisePrecision, correct, alerts);
    }

    public enum PromisePrecision {
        YEAR, MONTH, WEEK, DAY
    }

    public enum AlertType {
        ALERT1(16),
        ALERT2(17),
        ALERT3(18),
        ALERT4(19),
        ALERT5(20),
        ALERT6(21),
        ALERT7(22),
        ALERT8(23),
        ALERT9(24),
        ALERT10(25),
        ALERT11(26),
        ALERT13(28),
        ALERT14(29),
        ALERT15(30);

        private int value;

        AlertType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
