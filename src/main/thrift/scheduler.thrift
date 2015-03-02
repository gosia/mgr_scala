namespace java com.mgr.thrift.scheduler
namespace py scheduler

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
  2: Time start_time;
  3: Time end_time;
}

struct Room {
  1: Id id;
  2: list<string> terms;
  3: list<string> labels;
  4: i16 capacity;
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
  8: i16 students_num;
}

enum Algorithm {
  RANDOM
}

enum TaskStatus {
  NOT_STARTED,
  PROCESSING,
  FINISHED
}

struct TaskInfo {
  1: Id id;
  2: TaskStatus status;
}

struct PlaceAndTime {
  1: Id term;
  2: Id room;
}

struct Timetable {
  1: map<Id, PlaceAndTime> group_to_place_and_time;
  2: string human_readable;
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

  list<TaskInfo> getConfigTasks(
    1: Id configId;
  ) throws (
    1: SchedulerException se;
  )

  Id createTask(
    1: Id configId;
    2: Algorithm algorithm;
  ) throws (
    1: SchedulerException se;
  )

  void startTask(
    1: Id taskId;
  ) throws (
    1: SchedulerException se;
  )

  TaskStatus getTaskStatus(
    1: Id taskId;
  ) throws (
    1: SchedulerException se;
  )

  Timetable getTaskResult(
    1: Id taskId;
  ) throws (
    1: SchedulerException se;
  )

}
