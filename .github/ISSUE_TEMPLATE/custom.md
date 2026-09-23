---
name: Custom issue template
about: Describe this issue template's purpose here.
title: ''
labels: ''
assignees: ''

---

Looking for Quest 2, 3S and Pro testers!

OpenQuestTuner marks every setting as Experimental until someone has measured its effect on that headset. If you have 15 minutes:

1. Apply a profile to any game (for example 90 Hz, ×1.2, CPU 3, GPU 3, foveation Medium).
2. While the game runs, send the line from `adb logcat -s VrApi` that starts with FPS= (it shows the refresh rate, render scale SF, CPU/GPU levels and Fov).
3. Tell me your headset model and Horizon OS version.

I'll add your results to the compatibility table and switch the settings to Verified for your model, with credit.
