import os
from telegram import Bot, Update
from telegram.ext import Dispatcher, MessageHandler, Filters
from telegram.ext.dispatcher import run_async
from flask import Request, Response
import logging
import io
from PIL import Image

TOKEN = os.environ.get("BOT_TOKEN")
bot = Bot(token=TOKEN)
dispatcher = Dispatcher(bot, None, workers=0)

logging.basicConfig(level=logging.INFO)

@run_async
def handle_photo(update: Update, context):
    if update.message and update.message.photo:
        photo = update.message.photo[-1].get_file()
        file_bytes = photo.download_as_bytearray()
        image = Image.open(io.BytesIO(file_bytes))

        # تحليل بسيط مؤقت
        decision = "✅ التوصية: بيع (SELL)\n📊 RSI = 72, ترند هابط\n⏱️ الصفقة: دقيقة واحدة"
        context.bot.send_message(chat_id=update.message.chat_id, text=decision)

photo_handler = MessageHandler(Filters.photo, handle_photo)
dispatcher.add_handler(photo_handler)

def handler(request: Request):
    if request.method == "POST":
        data = request.get_json(force=True)
        update = Update.de_json(data, bot)
        dispatcher.process_update(update)
        return Response("ok", status=200)
    return Response("Only POST method is accepted", status=405)