namespace java com.mgr.thrift.scheduler
namespace py scheduler

typedef string Id

exception ValidationException {
  1: string message;
}

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

struct TeacherExtra {
  1: string first_name;
  2: string last_name;
  3: i16 pensum;
  4: string notes;
}

struct Teacher {
  1: Id id;
  2: list<string> terms;
  3: TeacherExtra extra;
}

struct GroupExtra {
  1: string course;
  2: string group_type;
  3: string notes;
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
  9: GroupExtra extra;
}

struct ConfigCreationInfo {
  1: Id id;
  2: i16 year;
  3: string term;
}

struct ConfigBasicInfo {
  1: Id id;
  2: i16 year;
  3: string term;
  4: optional Id file;
}

struct Config {
  1: ConfigBasicInfo info;
  2: list<Term> terms;
  3: list<Room> rooms;
  4: list<Teacher> teachers;
  5: list<Group> groups;
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

struct FileConfigs {
  1: Id configId1;
  2: Id configId2;
}

struct FileBasicInfo {
  1: Id id;
  2: i16 year;
  3: bool linked;
  4: optional FileConfigs configs;
}

struct File {
  1: FileBasicInfo info;
  2: string content;
}

service SchedulerService {

  void createConfig(
    1: ConfigCreationInfo info;
    2: list<Term> terms;
    3: list<Room> rooms;
    4: list<Teacher> teachers;
    5: list<Group> groups;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  Config getConfig(
      1: Id id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  void deleteConfig(
    1: Id config_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )
  
  list<ConfigBasicInfo> getConfigs(
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  void addConfigElement(
    1: Id id;
    2: list<Term> terms;
    3: list<Room> rooms;
    4: list<Teacher> teachers;
    5: list<Group> groups;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )
  
  void editConfigElement(
    1: Id id;
    2: list<Term> terms;
    3: list<Room> rooms;
    4: list<Teacher> teachers;
    5: list<Group> groups;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )
  
  void removeConfigElement(
    1: Id config_id;
    2: Id element_id;
    3: string element_type;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  void copyConfigElements(
    1: Id to_config_id;
    2: Id from_config_id;
    3: string elements_type;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  list<TaskInfo> getTasks(
    1: optional Id config_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  Id createTask(
    1: Id config_id;
    2: Algorithm algorithm;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  void startTask(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  void deleteTask(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  TaskStatus getTaskStatus(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  TaskInfo getTaskInfo(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  Timetable getTaskResult(
    1: Id task_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  Timetable addTaskEvent(
    1: Id task_id;
    2: Id group_id;
    3: Point point;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  Timetable removeTaskEvent(
    1: Id task_id;
    2: Id group_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  list<Id> getGroupBusyTerms(
    1: Id task_id;
    2: Id group_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  File createFile(
    1: FileCreationInfo info;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  void deleteFile(
    1: Id file_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  File getFile(
    1: Id file_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  list<FileBasicInfo> getFiles() throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  void saveFile(
    1: Id file_id;
    2: string content;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

  FileBasicInfo linkFile(
    1: Id file_id;
  ) throws (
    1: SchedulerException se;
    2: ValidationException ve;
  )

}
