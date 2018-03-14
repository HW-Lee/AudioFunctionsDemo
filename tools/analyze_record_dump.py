import os
import sys
import json
import struct
import datetime
import platform

import numpy as np
import matplotlib.pyplot as plt
from librosa.output import write_wav as wavwrite

if platform.system() == "Windows":
    SEP = "\\"
else:
    SEP = "/"

INFO_FILE = "info.json"
BIN_FILE = "stream.bin"
CONFIG_FILE = "parse_config.json"

PARSE_CONFIG_STR = [
    "# Parse Configuration",
    "# The record dump starts at \"{}\"",
    "# Dump information:",
    "#     - sampling frequency  : {} Hz",
    "#     - pcm dump duration   : {} sec.",
    "#     - record buffer length: {} ms.",
    "# Description of the fields:",
    "#     - pcm     : the pcm dump (.wav) from the timestamp after the offset",
    "#        - \"from\" and \"to\" are the signal range configuration for the output audio file with respect to sec.",
    "#     - signal  : the signal waveform (.png) from the timestamp after the offset",
    "#        - x-axis refers to the time index with respect to sec.",
    "#        - y-axis refers to the amplitude",
    "#     - spectrogram: the spectrogram (.png) from the timestamp after the offset",
    "#        - x-axis refers to the frame index with respect to a {}ms-length signal frame",
    "#        - y-axis refers to the frequency with respect to Hz",
    "\n"
]

NHEADER_LINES = len(PARSE_CONFIG_STR)
PARSE_CONFIG_STR = "\n".join(PARSE_CONFIG_STR)

class AudioSignalFrame(object):
    def __init__(self, info):
        self.name = info["name"]
        self.fs = info["fs"]
        self.datasize = info["datasize-in-double"]
        self.create_at = info["createAt"]
        self.data = None

def main(dir_name):
    if dir_name.endswith(SEP):
        dir_name = dir_name[:-1]

    parse_config = {}
    if os.path.exists("{}{}{}".format(dir_name, SEP, CONFIG_FILE)):
        with open("{}{}{}".format(dir_name, SEP, CONFIG_FILE), "r") as f:
            try:
                lines = f.readlines()
                parse_config = json.loads("".join(lines[NHEADER_LINES:]))
            except Exception as e:
                parse_config = {}

    with open("{}{}{}".format(dir_name, SEP, INFO_FILE), "r") as f:
        info = json.load(f)
        frames = map(AudioSignalFrame, info)

    with open("{}{}{}".format(dir_name, SEP, INFO_FILE), "w") as f:
        f.write(json.dumps(info, indent=4) + "\n")

    with open("{}{}{}".format(dir_name, SEP, BIN_FILE), "rb") as f:
        for frame in frames:
            frame.data = f.read(frame.datasize*8)
            frame.data = list(struct.unpack(">{}d".format(frame.datasize), frame.data))

    first_frame = filter(lambda x: x.name == "signal", frames)[0]
    last_frame = filter(lambda x: x.name == "signal", frames)[-1]
    log_starts_timestamp = first_frame.create_at
    log_ends_timestamp = last_frame.create_at
    fs = first_frame.fs * 1.0

    # Processing the signal packet
    signal_len = sum(map(lambda x: x.datasize, filter(lambda x: x.name == "signal", frames)))
    recbufsize_ms = np.round(1000.0*signal_len/fs/len(filter(lambda x: x.name == "spectrum", frames)))

    def to_time(timestr):
        ss = timestr.split()
        ss[-2] += "000"
        ss = " ".join(ss)
        return datetime.datetime.strptime(ss, "%Y-%m-%d %H:%M:%S.%f (UTF+8)")

    def diff_sec(tstr1, tstr2):
        return (to_time(tstr2) - to_time(tstr1)).total_seconds()

    duration = diff_sec(log_starts_timestamp, log_ends_timestamp) + recbufsize_ms/1000.0
    signal = -10 * np.ones([int(np.ceil(duration*fs)), 1])

    for signal_frame in filter(lambda x: x.name == "signal", frames):
        offset_sec = diff_sec(log_starts_timestamp, signal_frame.create_at)
        offset = int(np.round(offset_sec*fs))
        signal[offset:offset+signal_frame.datasize, 0] = np.array(signal_frame.data)

    missingxx, _ = np.where(signal < -9)
    signal = np.where(signal > -9, signal, 0)

    signal = signal / np.max(np.abs(signal)) * 0.9
    if "pcm" in parse_config.keys():
        pcmrange = np.array([parse_config["pcm"]["from"], parse_config["pcm"]["to"]])
        pcmrange = np.round(pcmrange*fs)
        pcmrange[0] = np.max([pcmrange[0], 0])
        pcmrange[1] = np.min([pcmrange[1], len(signal)])
        pcmrange = np.array(pcmrange, dtype=int)
    else:
        pcmrange = [0, len(signal)]
        parse_config["pcm"] = {
            "from": 0,
            "to": len(signal)*1.0/fs
        }

    wavwrite("{}{}pcmdump.wav".format(dir_name, SEP), signal[pcmrange[0]:pcmrange[1]], fs)

    xx = np.arange(len(signal))/fs
    plt.plot(xx, signal)
    plt.plot(missingxx/fs, [0]*len(missingxx), "r.", markersize=0.1)
    plt.legend(["signal", "missing frames"])
    if "signal" in parse_config.keys():
        plt.xlim(parse_config["signal"]["xlim"])
        plt.ylim(parse_config["signal"]["ylim"])
    else:
        parse_config["signal"] = {
            "xlim": plt.gca().get_xlim(),
            "ylim": plt.gca().get_ylim()
        }

    xlim = parse_config["signal"]["xlim"]
    plt.gcf().set_size_inches([xlim[1]-xlim[0], 3])
    plt.savefig("{}{}signal.png".format(dir_name, SEP), bbox_inches="tight", pad_inches=0, dpi=300)
    plt.gcf().clear()

    # Processing the spectrogram packet
    first_frame = filter(lambda x: x.name == "spectrum", frames)[0]
    last_frame = filter(lambda x: x.name == "spectrum", frames)[-1]
    log_starts_timestamp = first_frame.create_at
    log_ends_timestamp = last_frame.create_at

    spectrogram = -10 * np.ones([first_frame.datasize, int(np.ceil(duration*1000.0/recbufsize_ms))])

    for spectrum_frame in filter(lambda x: x.name == "spectrum", frames):
        offset_sec = diff_sec(log_starts_timestamp, spectrum_frame.create_at)
        offset = int(np.round(offset_sec*1000.0/recbufsize_ms))
        if spectrogram[0, offset] > -9:
            offset += 1
        spectrogram[:, offset] = np.array(spectrum_frame.data)

    spectrogram = spectrogram[:spectrogram.shape[0]/2, :]

    alpha_mask = np.array(spectrogram)
    alpha_mask = np.where(alpha_mask < -9, alpha_mask, 0)
    alpha_mask = np.where(alpha_mask > -9, alpha_mask, 0.4)
    mask = np.zeros([spectrogram.shape[0], spectrogram.shape[1], 4])
    mask[:, :, 0] = 1
    mask[:, :, 3] = alpha_mask

    spectrogram = np.where(spectrogram > -9, spectrogram, 0)

    spectrogram += 1e-32
    spectrogram = 20 * np.log10(spectrogram)


    plt.imshow(spectrogram, vmax=np.max(spectrogram), vmin=np.max(spectrogram)-40, cmap="gray", origin="lower")
    plt.colorbar()
    plt.imshow(mask, origin="lower")
    ticks = plt.gca().get_yticks()*1.0/spectrogram.shape[0] * fs/2.0
    ticks = np.array(np.round(ticks), dtype=int)
    plt.gca().set_yticklabels(ticks)
    plt.gca().set_ylabel("frequency (Hz)")
    plt.gca().set_xlabel("frame index ({} ms/frame)".format(recbufsize_ms))

    if "spectrogram" in parse_config.keys():
        plt.xlim(parse_config["spectrogram"]["xlim"])
        plt.ylim(np.array(parse_config["spectrogram"]["ylim"]) * 2.0/fs * spectrogram.shape[0])
    else:
        parse_config["spectrogram"] = {
            "xlim": plt.gca().get_xlim(),
            "ylim": list(np.array(plt.gca().get_ylim())*1.0/spectrogram.shape[0] * fs/2.0)
        }

    xlim = parse_config["spectrogram"]["xlim"]
    plt.gcf().set_size_inches([(xlim[1]-xlim[0])*recbufsize_ms/1000.0, 3])
    plt.savefig("{}{}spectrogram.png".format(dir_name, SEP), bbox_inches="tight", pad_inches=0, dpi=300)
    plt.gcf().clear()

    with open("{}{}{}".format(dir_name, SEP, CONFIG_FILE), "w") as f:
        f.write(PARSE_CONFIG_STR.format( \
            log_starts_timestamp, fs, len(signal)*1.0/fs, recbufsize_ms, recbufsize_ms))
        f.write(json.dumps(parse_config, indent=4) + "\n")

if __name__ == "__main__":
    main(sys.argv[1])
