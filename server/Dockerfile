FROM python:3.12-slim
WORKDIR /app
COPY server.py backup.py /app/
RUN useradd --uid 10001 --create-home dayflow && mkdir /data && chown dayflow /data
USER dayflow
ENV DAYFLOW_DB=/data/dayflow.db DAYFLOW_PORT=8765
EXPOSE 8765
VOLUME ["/data"]
CMD ["python", "server.py"]
