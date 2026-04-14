#!/usr/bin/python3

import subprocess

subprocess.run(["docker", "compose", "up", "-d"])

subprocess.run(["sbt", "run"])
