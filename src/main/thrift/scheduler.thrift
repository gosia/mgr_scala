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
  2: i16 hour;
  3: i16 minute;
}

struct Term {
  1: Id id;
  2: Time start_time;
  3: Time end_time;
  4: Day day;
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

struct GroupExtra {
  1: string course;
  2: string group_type;
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
  9: optional GroupExtra extra;
}

struct ConfigInfo {
  1: Id id;
  2: list<Term> terms;
  3: list<Room> rooms;
  4: list<Teacher> teachers;
  5: list<Group> groups;
}

struct ConfigBasicInfo {
  1: Id id;
  2: i16 year;
  3: string term; 
}

enum Algorithm {
  RANDOM,
  MANUAL
}

enum TaskStatus {
  NOT_STARTED,
  PROCESSING,
  FINISHED
}

enum Mode {
  ADD,
  EDIT
}

struct TaskInfo {
  1: Id id;
  2: Id config_id;
  3: TaskStatus status;
  4: Algorithm algorithm;
}

struct PlaceAndTime {
  1: Id term;
  2: Id room;
}

struct Timetable {
  1: map<Id, list<PlaceAndTime>> group_to_place_and_time;
  2: string human_readable;
}

service SchedulerService {

  void createConfig(
    1: ConfigBasicInfo info;
    2: list<Term> terms;
    3: list<Room> rooms;
    4: list<Teacher> teachers;
    5: list<Group> groups;
  ) throws (
    1: SchedulerException se;
  )

  ConfigInfo getConfigInfo(
      1: Id id;
  ) throws (
    1: SchedulerException se;
  )
  
  list<ConfigBasicInfo> getConfigs(
  ) throws (
    1: SchedulerException se;
  )

  void addConfigElement(
    1: Id id;
    2: Mode mode;
    3: list<Term> terms;
    4: list<Room> rooms;
    5: list<Teacher> teachers;
    6: list<Group> groups;
  ) throws (
    1: SchedulerException se;
  )
  
  void removeConfigElement(
    1: Id config_id;
    2: Id element_id;
    3: string element_type;
  ) throws (
    1: SchedulerException se;
  )

  list<TaskInfo> getTasks(
    1: optional Id configId;
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

  void deleteTask(
    1: Id taskId;
  ) throws (
    1: SchedulerException se;
  )

  TaskStatus getTaskStatus(
    1: Id taskId;
  ) throws (
    1: SchedulerException se;
  )

  TaskInfo getTaskInfo(
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
