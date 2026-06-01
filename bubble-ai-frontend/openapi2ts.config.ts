//根据后端接口生成前端请求TS代码
export default {
  requestLibPath: "import request from '@/request'",
  schemaPath: 'http://localhost:8123/api/v3/api-docs',
  serversPath: './src',
  hook: {
    // Java Long values may exceed Number.MAX_SAFE_INTEGER. Keep them lossless at the API boundary.
    customType(schemaObject: { type?: string; format?: string } | undefined) {
      if (schemaObject?.type === 'integer' && schemaObject.format === 'int64') {
        return 'string'
      }
    },
  },
}
