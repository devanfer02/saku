# CORD v2 fixtures

The `cord_*.txt` files are OCR text derived from the images of the CORD v2 test split
(100 Indonesian receipts), read on an Android emulator with ML Kit text recognition 16.0.1.
Each line is `left,top,right,bottom<TAB>text`. `totals.tsv` holds each receipt's labelled
total (`gt_parse.total.total_price`), with sen dropped; `-` marks a receipt with no label.
`items.tsv` holds each receipt's labelled menu lines (`gt_parse.menu[].nm` and `price`),
with sen dropped.

The images are not included.

Source: CORD, a Consolidated Receipt Dataset for post-OCR parsing, by NAVER CLOVA AI.
https://github.com/clovaai/cord and https://huggingface.co/datasets/naver-clova-ix/cord-v2

Licence: Creative Commons Attribution 4.0 International (CC BY 4.0),
https://creativecommons.org/licenses/by/4.0/. Changes: the images were run through OCR and
only the recognized text, the total labels and the menu name and price labels are kept here.
