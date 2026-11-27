package it.giovaganzerli.planning;

final class CalendarChoice {
    final long id;
    final String name, account;
    CalendarChoice(long id, String name, String account) {
        this.id = id;
        this.name = name == null ? "Calendario Google" : name;
        this.account = account == null ? "" : account;
    }
}
