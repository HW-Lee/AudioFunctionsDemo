import os
import sys
import json
import struct
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

PARSE_CONFIG_STR = \
"# Parse Configuration\n" + \
"# The record dump starts at \"{}\"\n" + \
"# Dump information:\n" + \
"#     - sampling frequency  : {} Hz\n" + \
"#     - pcm dump duration   : {} sec.\n" + \
"#     - record buffer length: {} ms.\n" + \
"# Description of the fields:\n" + \
"#     - pcm     : the pcm dump (.wav) from the timestamp after the offset\n" + \
"#        - \"from\" and \"to\" are the signal range configuration for the output audio file with respect to sec.\n" + \
"#     - signal  : the signal waveform (.png) from the timestamp after the offset\n" + \
"#        - x-axis refers to the time index with respect to sec.\n" + \
"#        - y-axis refers to the amplitude\n" + \
"#     - spectrogram: the spectrogram (.png) from the timestamp after the offset\n" + \
"#        - x-axis refers to the frame index with respect to a {}ms-length signal frame\n" + \
"#        - y-axis refers to the frequency with respect to Hz\n\n"

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
                parse_config = json.load(f)
            except Exception as e:
                parse_config = {}

    with open("{}{}{}".format(dir_name, SEP, INFO_FILE), "r") as f:
        info = json.load(f)
        frames = map(AudioSignalFrame, info)

    with open("{}{}{}".format(dir_name, SEP, BIN_FILE), "rb") as f:
        for frame in frames:
            frame.data = f.read(frame.datasize*8)
            frame.data = struct.unpack(">{}d".format(frame.datasize), frame.data)

    first_frame = filter(lambda x: x.name == "signal", frames)[0]
    log_starts_timestamp = first_frame.create_at
    fs = first_frame.fs * 1.0
    signal = np.array(reduce(lambda x, y: x+y, map(lambda x: x.data, filter(lambda x: x.name == "signal", frames))))
    spectrogram = np.array(map(lambda x: x.data, filter(lambda x: x.name == "spectrum", frames))).transpose()
    spectrogram = spectrogram[:spectrogram.shape[0]/2, :]
    spectrogram += 1e-32
    spectrogram = 20 * np.log10(spectrogram)

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
    if "signal" in parse_config.keys():
        plt.xlim(parse_config["signal"]["xlim"])
        plt.ylim(parse_config["signal"]["ylim"])
    else:
        parse_config["signal"] = {
            "xlim": plt.gca().get_xlim(),
            "ylim": plt.gca().get_ylim()
        }

    plt.gcf().set_size_inches([xx[-1], 3])
    plt.savefig("{}{}signal.png".format(dir_name, SEP), bbox_inches="tight", pad_inches=0, dpi=300)
    plt.gcf().clear()

    plt.imshow(spectrogram, vmax=np.max(spectrogram), vmin=np.max(spectrogram)-40, cmap="gray", origin="lower")
    plt.colorbar()
    ticks = plt.gca().get_yticks()*1.0/spectrogram.shape[0] * fs/2.0
    ticks = np.array(np.round(ticks), dtype=int)
    plt.gca().set_yticklabels(ticks)
    plt.gca().set_ylabel("frequency (Hz)")
    rec_buff_len = np.round(1000*xx[-1]/spectrogram.shape[1])
    plt.gca().set_xlabel("frame index ({} ms/frame)".format(rec_buff_len))
    if "spectrogram" in parse_config.keys():
        plt.xlim(parse_config["spectrogram"]["xlim"])
        plt.ylim(np.array(parse_config["signal"]["ylim"]) * 2.0/fs * spectrogram.shape[0])
    else:
        parse_config["spectrogram"] = {
            "xlim": plt.gca().get_xlim(),
            "ylim": list(np.array(plt.gca().get_ylim())*1.0/spectrogram.shape[0] * fs/2.0)
        }

    plt.savefig("{}{}spectrogram.png".format(dir_name, SEP), bbox_inches="tight", pad_inches=0, dpi=300)
    plt.gcf().clear()

    with open("{}{}{}".format(dir_name, SEP, CONFIG_FILE), "w") as f:
        f.write(PARSE_CONFIG_STR.format( \
            log_starts_timestamp, fs, len(signal)*1.0/fs, rec_buff_len, rec_buff_len))
        f.write(json.dumps(parse_config, indent=4) + "\n")

if __name__ == "__main__":
    main(sys.argv[1])
