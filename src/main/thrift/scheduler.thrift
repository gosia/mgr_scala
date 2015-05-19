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
  1: i16 hour;
  2: i16 minute;
}

struct Term {
  1: Id id;
  2: Time start_time;
  3: Time end_time;
  4: Day day;
}

struct Point {
  1: Time time;
  2: Day day;
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

struct GroupRoomTerm {
  1: Id group_id;
  2: Id room_id;
  3: Id term_id;
}

struct Timetable {
  1: map<Id, list<PlaceAndTime>> group_to_place_and_time;
  2: string human_readable;
}

struct FileCreationInfo {
  1: Id id;
  2: i16 year;
}

struct FileBasicInfo {
  1: Id id;
  2: i16 year;
  3: bool linked;
}

struct File {
  1: FileBasicInfo info;
  2: string content;
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

  void deleteConfig(
    1: Id config_id;
  ) throws (
    1: SchedulerException se;
  )
  
  list<ConfigBasicInfo> getConfigs(
  ) throws (
    1: SchedulerException se;
  )

  void addConfigElement(
    1: Id id;
    2: list<Term> terms;
    3: list<Room> rooms;
    4: list<Teacher> teachers;
    5: list<Group> groups;
  ) throws (
    1: SchedulerException se;
  )
  
  void editConfigElement(
    1: Id id;
    2: list<Term> terms;
    3: list<Room> rooms;
    4: list<Teacher> teachers;
    5: list<Group> groups;
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

  void copyConfigElements(
    1: Id to_config_id;
    2: Id from_config_id;
    3: string elements_type;
  ) throws (
    1: SchedulerException se;
  )

  void importData(
    1: Id config_id;
    2: string data;
  ) throws (
    1: SchedulerException se;
  )

  list<TaskInfo> getTasks(
    1: optional Id config_id;
  ) throws (
    1: SchedulerException se;
  )

  Id createTask(
    1: Id config_id;
    2: Algorithm algorithm;
  ) throws (
    1: SchedulerException se;
  )

  void startTask(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
  )

  void deleteTask(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
  )

  TaskStatus getTaskStatus(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
  )

  TaskInfo getTaskInfo(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
  )

  Timetable getTaskResult(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
  )

  Timetable addTaskEvent(
    1: Id task_id;
    2: Id group_id;
    3: Point point;
  ) throws (
    1: SchedulerException se;
  )

  Timetable removeTaskEvent(
    1: Id task_id;
    2: Id group_id;
  ) throws (
    1: SchedulerException se;
  )

  list<Id> getGroupBusyTerms(
    1: Id task_id;
    2: Id group_id;
  ) throws (
    1: SchedulerException se;
  )

  File createFile(
    1: FileCreationInfo info;
  ) throws (
    1: SchedulerException se;
  )

  void deleteFile(
    1: Id file_id;
  ) throws (
    1: SchedulerException se;
  )

  File getFile(
    1: Id file_id;
  ) throws (
    1: SchedulerException se;
  )

  list<FileBasicInfo> getFiles() throws (
    1: SchedulerException se;
  )

  void saveFile(
    1: Id file_id;
    2: string content;
  ) throws (
    1: SchedulerException se;
  )

}
