

from pydantic import BaseModel


class A(BaseModel):
    _abc: str = str('abc')
    __cde: str = str('abc')


class B(A):
    _efg: str = str('abc')
    __hij: str = 1

B(<caret>)