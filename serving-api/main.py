import json
import os
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import Depends, FastAPI, HTTPException
from redis import Redis
from redis.exceptions import RedisError


@asynccontextmanager
async def lifespan(app: FastAPI):
    """App lifespan 훅에서 Redis를 초기화/정리."""
    global redis_client
    try:
        redis_host = os.environ.get("REDIS_HOST", "localhost")
        redis_port = int(os.environ.get("REDIS_PORT", "6379"))
        redis_client = Redis(
            host=redis_host,
            port=redis_port,
            db=0,
            decode_responses=True,
            socket_connect_timeout=2,
            socket_timeout=2,
        )
        redis_client.ping()
        print(f"Successfully connected to Redis at {redis_host}:{redis_port}.")
    except Exception as e:
        print(f"Could not connect to Redis: {e}")
        redis_client = None

    yield

    if redis_client:
        try:
            redis_client.close()
        except Exception:
            pass


app = FastAPI(
    title="CTR Serving API",
    description="An API to serve real-time and previous Click-Through-Rate data.",
    version="1.1.0",
    lifespan=lifespan,
)

redis_client: Optional[Redis] = None


def get_redis() -> Redis:
    if not redis_client:
        raise HTTPException(status_code=503, detail="Redis service is unavailable.")
    return redis_client


# --- API Endpoints ---

@app.get("/")
def read_root():
    """
    A welcome message and API status.
    """
    return {
        "message": "Welcome to the CTR Serving API",
        "redis_status": "connected" if redis_client else "disconnected"
    }


@app.get("/ctr/latest", summary="Get Latest CTR for All Products")
def get_all_latest_ctr(redis: Redis = Depends(get_redis)):
    """
    Retrieves the latest CTR data for all products from the 'ctr:latest' Redis hash.
    """
    try:
        ctr_hash = redis.hgetall("ctr:latest")
        if not ctr_hash:
            return {}
        result = {key: json.loads(value) for key, value in ctr_hash.items()}
        return result
    except RedisError as e:
        raise HTTPException(status_code=500, detail=f"An error occurred: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Unexpected error: {str(e)}")


@app.get("/ctr/previous", summary="Get Previous CTR for All Products")
def get_all_previous_ctr(redis: Redis = Depends(get_redis)):
    """
    Retrieves the previous CTR data for all products from the 'ctr:previous' Redis hash.
    """
    try:
        ctr_hash = redis.hgetall("ctr:previous")
        if not ctr_hash:
            return {}
        result = {key: json.loads(value) for key, value in ctr_hash.items()}
        return result
    except RedisError as e:
        raise HTTPException(status_code=500, detail=f"An error occurred: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Unexpected error: {str(e)}")


@app.get("/ctr/{product_id}", summary="Get Latest and Previous CTR for a Product")
def get_ctr_by_product_id(product_id: str, redis: Redis = Depends(get_redis)):
    """
    Retrieves both the latest and previous CTR data for a specific **product_id**.
    """
    try:
        latest_json = redis.hget("ctr:latest", product_id)
        previous_json = redis.hget("ctr:previous", product_id)

        if not latest_json and not previous_json:
            raise HTTPException(
                status_code=404,
                detail=f"No CTR data found for product_id '{product_id}'."
            )

        return {
            "latest": json.loads(latest_json) if latest_json else None,
            "previous": json.loads(previous_json) if previous_json else None
        }
    except RedisError as e:
        raise HTTPException(status_code=500, detail=f"An error occurred: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Unexpected error: {str(e)}")


# To run this app locally:
# 1. cd serving-api
# 2. uv venv && source .venv/bin/activate
# 3. uv pip install -e .
# 4. uvicorn main:app --host 0.0.0.0 --port 8000 --reload
