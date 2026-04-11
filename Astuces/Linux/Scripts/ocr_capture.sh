#!/bin/bash
flameshot gui -r > /tmp/capture.png
tesseract /tmp/capture.png stdout -l fra | xclip -selection clipboard
