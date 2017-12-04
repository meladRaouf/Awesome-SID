import datetime
import os
import platform
import queue
import subprocess
import sys
import threading
import time

from logging import Formatter, Logger, getLogger, DEBUG, StreamHandler, FileHandler, INFO, WARN, ERROR, CRITICAL

# Use the custom version of adb that configures devices by their usb port rather than serial number
ADB = 'adb' if platform.system() == 'Windows' else './testing/adb'
GRADLEW_PATH = 'gradlew.bat' if platform.system() == 'Windows' else './gradlew'
CERBERUS_DIR_PATH = '../Android-Cerberus/'

buckets = {
    'bucket_01': 'com.simprints.id.bucket01.Bucket01Suite',
}

commands = {
    'clean cerberus build': 'cd ' + CERBERUS_DIR_PATH + ';' + GRADLEW_PATH + ' :cerberus-app:clean',
    'clean simprints id build': GRADLEW_PATH + ' :id:clean',
    'assemble cerberus debug apk': 'cd ' + CERBERUS_DIR_PATH + ';' + GRADLEW_PATH + ' :cerberus-app:assembleDebug',
    'assemble simprints id endToEndTesting apk': (GRADLEW_PATH + ' :id:assembleEndToEndTesting'),
    'assemble simprints id endToEndTesting test apk': (GRADLEW_PATH + ' :id:assembleEndToEndTestingAndroidTest'),
    'devices query': ADB + ' devices -l',
    'install cerberus debug apk': ADB + ' -s {0} install -t -d -r ' + CERBERUS_DIR_PATH + 'cerberus-app/build/outputs'
                                                                                          '/apk/debug/cerberus-app'
                                                                                          '-debug.apk',
    'install simprints id endToEndTesting apk': ADB + ' -s {0} install -t -d -r '
                                                      'id/build/outputs/apk/endToEndTesting/id-endToEndTesting.apk',
    'install simprints id endToEndTesting test apk': ADB + ' -s {0} install -t -d -r '
                                                           'id/build/outputs/apk/androidTest/endToEndTesting/id'
                                                           '-endToEndTesting-androidTest.apk',
    'run test': ADB + ' -s {0} shell am instrument -w '
                      '-e class {1} com.simprints.id.test/android.support.test.runner.AndroidJUnitRunner '
}


class Scanner:
    def __init__(self, scanner_id: str, mac_address: str, hardware_version: int, description: str = ''):
        self.scanner_id: str = scanner_id
        self.mac_address: str = mac_address
        self.hardware_version: int = hardware_version
        self.description: str = description


scanners = {
    'SP576290': Scanner('SP576290', 'F0:AC:D7:C8:CB:22', 6),
    'SP337428': Scanner('SP337428', 'F0:AC:D7:C5:26:14', 6),
    'SP443761': Scanner('SP443761', 'F0:AC:D7:C6:C5:71', 6),
    'SP898185': Scanner('SP898185', 'F0:AC:D7:CD:B4:89', 4)
}


class Device:
    def __init__(self, device_id: str, model: str):
        self.device_id: str = device_id
        self.model: str = model


class LogState:
    """
    These methods return a LogState to be passed to Run.updateLogFormat()
    They represent different beginning strings for each of the logs corresponding to what state the program is in.
    Here are the states and when they should be used:

    default()
    [time] name :
    This is the base line for the whole log. All other methods call this one. This is for commands and log output that
    is not directed at any particular device.

    device(arg1: Device)
    [time] name : phone model :
    This is for when commands are directed at a particular device, usually to change the state of the device.

    test(arg1: Device)
    [time] name : phone model : device state :
    This is for executing a test with a preset, known, constant device state.


    """

    @staticmethod
    def default(extra=''):
        fmt = '[%(asctime)s] :{0} %(message)s'.format(extra)
        datefmt = '%Y/%m/%d %H:%M:%S'

        return Formatter(fmt=fmt, datefmt=datefmt)

    @staticmethod
    def device(device: Device, extra=''):
        return LogState.default(' {0:12s} :{1}'.format(device.model, extra))

    @staticmethod
    def test(device: Device, bucket: str):
        return LogState.device(device, ' {0:9s} :'.format(bucket))


class Run:
    LOG_DIR_BASE_NAME = 'testing/logs'

    if not os.path.exists(LOG_DIR_BASE_NAME):
        os.makedirs(LOG_DIR_BASE_NAME)

    def __init__(self, logger_name, log_commands=True):

        self.log_dir_name = Run.LOG_DIR_BASE_NAME + '/' \
                            + logger_name + '_' + datetime.datetime.now().strftime('%Y-%m-%d_%H-%M-%S')

        if not os.path.exists(self.log_dir_name):
            os.makedirs(self.log_dir_name)

        self.logger: Logger = getLogger(logger_name)
        self.logger.setLevel(DEBUG)

        self.console_handler: StreamHandler = StreamHandler(sys.stdout)
        self.file_handler: FileHandler = FileHandler(self.log_dir_name + '/' + logger_name + '.log', mode='w')

        self.logger.addHandler(self.console_handler)
        self.logger.addHandler(self.file_handler)

        self.update_log_format(LogState.default())

        self.log_commands = log_commands

    @staticmethod
    def reformat_process_output(output: bytes):
        #  The output onto the command line contains a lot of \r and \n characters which add a lot of blank spaces
        return output.decode('utf-8').replace(u'\r\r\n', '').replace(u'\r\n', '').replace(u'\n', '')

    def update_log_format(self, log_state: Formatter, extra_file_handler: FileHandler = None):
        self.console_handler.setFormatter(log_state)
        self.file_handler.setFormatter(log_state)
        if extra_file_handler is not None:
            extra_file_handler.setFormatter(log_state)

    def log(self, line: str, flag=INFO):
        if flag is DEBUG:
            self.logger.debug(line)
        elif flag is INFO:
            self.logger.info(line)
        elif flag is WARN:
            self.logger.warning(line)
        elif flag is ERROR:
            self.logger.error(line)
        elif flag is CRITICAL:
            self.logger.critical(line)
        else:
            self.logger.info(line)

    def run_and_log(self, command):
        if self.log_commands:
            self.log('>>> ' + command, DEBUG)
        lines = []
        if platform.system() == 'Windows':
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=1)
        else:
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=1, shell=True)

        def enqueue_output(out, this_queue: queue.Queue):
            for thisLine in iter(out.readline, b''):
                this_queue.put(thisLine)
            out.close()
            this_queue.put(None)

        message_queue = queue.Queue()
        thread = threading.Thread(target=enqueue_output, args=(process.stdout, message_queue))
        thread.daemon = True
        thread.start()

        while True:
            while not message_queue.empty():
                line = message_queue.get()
                message_queue.task_done()
                if line is None:
                    return lines
                formatted_line = self.reformat_process_output(line)

                lines.append(formatted_line)
                self.log(formatted_line, INFO)

    ##############
    #  Command methods
    #
    #  Template:
    #
    #  command_method_name(self, ...):
    #      self.update_log_format(LogSate.appropriate_log_state())
    #      lines = self.run_and_log(command['the_command'].format(the, command, arguments))
    #
    #      processed_lines = process_the_raw_outputted_lines(lines)
    #      update_state_or_logs_if_necessary(processed_lines)
    #
    #      return nothing_or_some_information_if_necessary
    #
    ##############

    def clean_cerberus_build(self):
        self.run_and_log(commands['clean cerberus build'])

    def clean_simprints_id_build(self):
        self.run_and_log(commands['clean simprints id build'])

    def assemble_cerberus_apk(self):
        self.run_and_log(commands['assemble cerberus debug apk'])

    def assemble_simprints_id_apk(self):
        self.run_and_log(commands['assemble simprints id endToEndTesting apk'])

    def assemble_simprints_id_test_apk(self):
        self.run_and_log(commands['assemble simprints id endToEndTesting test apk'])

    def install_cerberus_apk(self, device: Device):
        self.run_and_log(commands['install cerberus debug apk'].format(device.device_id))

    def install_apk(self, device: Device):
        self.run_and_log(commands['install simprints id endToEndTesting apk'].format(device.device_id))

    def install_test_apk(self, device: Device):
        self.run_and_log(commands['install simprints id endToEndTesting test apk'].format(device.device_id))

    def devices_query(self):
        lines = self.run_and_log(commands['devices query'])

        # The first line is "List of devices attached". The last line is blank. Lines in between contain a device.
        # If the ADB daemon isn't started or is invalid, there is some preamble that cane ignored.
        relevant_lines = []
        for line in lines[1:-1]:
            if line[0] != '*':
                relevant_lines.append(line)
        devices_strs = []
        for line in relevant_lines:
            devices_strs.append(line.split())
        devices = []
        for deviceStr in devices_strs:
            # The 0th element is the device Id
            # The 3rd element is the model name, the first 6 characters are 'model:'
            for deviceStrSection in deviceStr:
                if deviceStrSection[0:6] == 'model:':
                    devices.append(Device(deviceStr[0], deviceStrSection[6:]))
        return devices

    def run_test(self, device: Device, test_id: str):
        test_dir_name = self.log_dir_name + '/' + device.model + '/' + test_id

        if not os.path.exists(test_dir_name):
            os.makedirs(test_dir_name)

        test_file_handler: FileHandler = FileHandler(test_dir_name + '/' + test_id + '.log', mode='w')

        self.logger.addHandler(test_file_handler)
        self.update_log_format(LogState.test(device, test_id), test_file_handler)

        self.run_and_log(commands['run test'].format(device.device_id, buckets[test_id]))

        self.logger.removeHandler(test_file_handler)


def main():
    start_time = time.perf_counter()

    run = Run('instrumented_test')
    run.update_log_format(LogState.default())
    run.log("Hello world!")

    run.clean_cerberus_build()
    run.assemble_cerberus_apk()

    run.clean_simprints_id_build()
    run.assemble_simprints_id_apk()
    run.assemble_simprints_id_test_apk()

    devices = run.devices_query()

    for device in devices:
        run.update_log_format(LogState.device(device))

        run.install_cerberus_apk(device)
        run.install_apk(device)
        run.install_test_apk(device)

        run.run_test(device, 'bucket_01')

    run.update_log_format(LogState.default())
    run.log('TEST END')

    end_time = time.perf_counter()
    run.log('Total time elapsed: {0}'.format(end_time - start_time))


if __name__ == "__main__":
    main()
