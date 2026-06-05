import dayjs from 'dayjs'

export const formatDate = (value?: string, pattern = 'YYYY-MM-DD', fallback = '-') =>
  value ? dayjs(value).format(pattern) : fallback
