namespace java com.mgr.thrift.scheduler

typedef string Id

exception SchedulerException {
  1: string message;
}

enum Day {
  MON,
  TUE,
  WED,
  THU,
  FRI,
  SAT,
  SUN
}

struct Time {
  1: Day day;
  2: i16 hour;
  3: i16 minute;
}

struct Term {
  1: Id id;
  2: Time start;
  3: Time end;
}

struct Room {
  1: Id id;
  2: list<string> terms;
  3: list<string> labels;
}

struct Teacher {
  1: Id id;
  2: list<string> terms;
}

struct Group {
  1: Id id;
  2: list<string> teachers;
  3: list<string> terms;
  4: list<string> labels;
  5: list<string> diff_term_groups;
  6: list<string> same_term_groups;
  7: i16 terms_num;
}

service SchedulerService {

  void createConfig(
    1: Id id;
    2: list<Term> terms;
    3: list<Room> rooms;
    4: list<Teacher> teachers;
    5: list<Group> groups;
  ) throws (
    1: SchedulerException se;
  )

}
