# QUIC_traffic
Encrypted network for some QUIC-based services

Please follow the link in Google Driver to download the dataset:
https://drive.google.com/drive/folders/1cwHhzvaQbi-ap8yfrj2vHyPmUTQhaYOj


###################

This dataset is collected during few weeks(starting March 2018) via Wireshark. To capture the network flows of video streaming, chat, and voice call services, we use Selenium WebDriver [1] in Google Chrome running on Ubuntu 16.04 OS. Besides, we also use quic-go [2] to transfer some files from server to client using QUIC and capture the network flows of file transfer services.


###################

References
1. Selenium, “Webdriver,” https://www.seleniumhq.org/projects/webdriver/,
April 2017.
2. lucas clemente, “A quic implementation in pure go,” https://github.com/lucas-clemente/quic-go, April 2017
